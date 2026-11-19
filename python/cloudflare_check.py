# SPDX-License-Identifier: 0BSD
# Copyright (c) 2024-2026 zodac.net
"""Passes the Cloudflare verification check on a tab of a running browser, using zendriver.

The browser is not launched here: it is the one started by the Java application (through chromedriver), and zendriver is attached to it
through the Chrome DevTools Protocol only for the duration of the check, then detached without closing it.

Usage:
    python3 cloudflare_check.py --address <host>:<port> --target <target ID> [--timeout <seconds>]

Prints a single JSON line to stdout, with a "result" of:
    - NOT_PRESENT: no challenge appeared
    - PASSED:      the challenge was passed
    - FAILED:      the challenge was not passed (with a "detail")
    - ERROR:       the check could not be performed (with a "detail")

Everything else (including zendriver's own logging) is written to stderr.
"""

import argparse
import asyncio
import json
import logging
import sys
from typing import Any

import asyncio_atexit
import zendriver as zd
from zendriver.core import util
from zendriver.core.cloudflare import cf_is_interactive_challenge_present, verify_cf
from zendriver.core.tab import Tab

LOGGER = logging.getLogger("cloudflare_check")

# How long to wait for a challenge to appear before assuming that Cloudflare has not presented one
CHALLENGE_APPEARANCE_TIMEOUT_SECONDS = 10
# The inputs that Cloudflare fills in once a challenge is passed, in the order that zendriver checks them
RESPONSE_INPUT_SELECTORS = ("input[name=cf-turnstile-response]", "input[name=cf_challenge_response]")


def find_tab(browser: zd.Browser, target_id: str) -> Tab:
    """Find the tab of the browser with the given DevTools target ID (the window handle of the tab in chromedriver).

    Returns:
        The tab with the target ID.

    Raises:
        LookupError: If the browser has no tab with the target ID.
    """
    for tab in browser.tabs:
        if tab.target_id == target_id:
            return tab
    available = [tab.target_id for tab in browser.tabs]
    message = f"No tab with target ID '{target_id}', found: {available}"
    raise LookupError(message)


async def is_response_missing(tab: Tab) -> bool:
    """Check whether a Cloudflare response input is still on the page without a value, meaning the challenge has not been passed.

    Returns:
        True if a response input is found without a value, otherwise False.
    """
    for selector in RESPONSE_INPUT_SELECTORS:
        response_input = await tab.query_selector(selector)
        if response_input is not None:
            await response_input.update()
            return not response_input.attrs.get("value")
    return False


async def check(host: str, port: int, target_id: str, solve_timeout: float) -> dict[str, Any]:
    """Attach to the browser, pass the challenge on the tab if there is one, then detach.

    Returns:
        The result of the check, to be printed as JSON.
    """
    # With both a host and a port, zendriver connects to the running browser instead of launching one
    browser = await zd.start(host=host, port=port)
    try:
        tab = find_tab(browser, target_id)
        if not await cf_is_interactive_challenge_present(tab, timeout=CHALLENGE_APPEARANCE_TIMEOUT_SECONDS):
            return {"result": "NOT_PRESENT"}

        try:
            await verify_cf(tab, timeout=solve_timeout)
        except TimeoutError as e:
            return {"result": "FAILED", "detail": str(e)}

        # verify_cf returns without an error if it cannot find a response input, so check that one is not still empty
        if await is_response_missing(tab):
            return {"result": "FAILED", "detail": "The Cloudflare response is still empty"}
        return {"result": "PASSED"}
    finally:
        await detach(browser)


async def detach(browser: zd.Browser) -> None:
    """Close zendriver's connections to the browser, without closing the browser.

    zendriver's own stop() (also registered to run when the event loop closes) sends 'Browser.close', which would close the browser that
    belongs to the Java application, so it is unregistered and never called.
    """
    asyncio_atexit.unregister(browser._atexit_cleanup)  # ruff: ignore[private-member-access] - the only way to stop zendriver closing the browser on exit
    util.get_registered_instances().discard(browser)
    for target in browser.targets:
        await target.aclose()
    if browser.connection is not None:
        await browser.connection.aclose()


def parse_address(address: str) -> tuple[str, int]:
    """Split a DevTools address ('host:port') into its host and port.

    Returns:
        The host and port of the address.

    Raises:
        ValueError: If the address is not a host and a numeric port.
    """
    host, _, port = address.rpartition(":")
    if not host or not port.isdigit():
        message = f"Invalid DevTools address, expected 'host:port', found: '{address}'"
        raise ValueError(message)
    return host, int(port)


def main() -> None:
    """Run the check, and print its result as a single JSON line."""
    parser = argparse.ArgumentParser(description="Passes the Cloudflare verification check on a tab of a running browser")
    parser.add_argument("--address", required=True, help="The DevTools address of the browser, as 'host:port'")
    parser.add_argument("--target", required=True, help="The DevTools target ID of the tab")
    parser.add_argument("--timeout", type=float, default=45, help="How long to try to pass the challenge, in seconds")
    parser.add_argument("--debug", action="store_true", help="Log zendriver's debug messages to stderr")
    arguments = parser.parse_args()
    logging.basicConfig(stream=sys.stderr, level=logging.DEBUG if arguments.debug else logging.WARNING, format="%(name)s: %(message)s")

    try:
        host, port = parse_address(arguments.address)
        result = asyncio.run(check(host, port, arguments.target, arguments.timeout))
    except Exception as e:  # any failure is reported to the Java application as a result, not a stack trace
        LOGGER.exception("Unable to perform the Cloudflare check")
        result = {"result": "ERROR", "detail": repr(e)}
    sys.stdout.write(json.dumps(result) + "\n")
    sys.stdout.flush()


if __name__ == "__main__":
    main()
