/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2024-2026 zodac.net
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package net.zodac.tracker.framework.cloudflare;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.zodac.tracker.framework.ExecutionCancellation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Passes a Cloudflare verification check using the {@code zendriver} Python library, instead of the {@link CloudflareSolver}.
 *
 * <p>
 * The browser is not replaced: a Python script ({@code python/cloudflare_check.py}) is started for each check, attaches {@code zendriver} to the
 * browser already created for the tracker (through the Chrome DevTools Protocol, at the address that {@code chromedriver} reports), passes the
 * challenge on the tracker's tab with {@code zendriver}'s {@code verify_cf}, then detaches without closing the browser. The script prints its
 * result as a single JSON line, and its logging is logged here at {@code DEBUG}.
 *
 * <p>
 * The script runs as a child process of the JVM, so it is killed with the browsers when an execution is cancelled.
 */
public final class ZendriverCloudflareSolver {

    private static final Logger LOGGER = LogManager.getLogger();

    // The Python interpreter (of the virtual environment with zendriver installed) and the script, overridable for local runs outside Docker
    private static final String PYTHON_EXECUTABLE = System.getProperty("tracker-profiles.zendriver.python", "/app/python/venv/bin/python");
    private static final String SCRIPT_PATH = System.getProperty("tracker-profiles.zendriver.script", "/app/python/cloudflare_check.py");
    private static final String CHROME_OPTIONS_CAPABILITY = "goog:chromeOptions";
    private static final String DEBUGGER_ADDRESS_KEY = "debuggerAddress";

    // How long the script tries to pass the challenge, matching the CloudflareSolver
    private static final Duration CHALLENGE_SOLVE_TIMEOUT = Duration.ofSeconds(45L);
    // Extra time for the script to start, wait for the challenge to appear, and attach to and detach from the browser
    private static final Duration PROCESS_OVERHEAD = Duration.ofSeconds(30L);

    private final RemoteWebDriver driver;

    private ZendriverCloudflareSolver(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Creates a {@link ZendriverCloudflareSolver} for the provided {@link RemoteWebDriver}.
     *
     * @param driver the {@link RemoteWebDriver} currently showing the Cloudflare verification check
     * @return the {@link ZendriverCloudflareSolver}
     */
    public static ZendriverCloudflareSolver forDriver(final RemoteWebDriver driver) {
        return new ZendriverCloudflareSolver(driver);
    }

    /**
     * Attempts to pass the Cloudflare verification check on the current page, with {@code zendriver}.
     *
     * @return the {@link CloudflareResult} of the attempt (passed if no challenge appeared)
     */
    public CloudflareResult solve() {
        final String debuggerAddress = debuggerAddress();
        if (debuggerAddress == null) {
            LOGGER.warn("Browser did not report its DevTools address, unable to automatically pass Cloudflare verification with zendriver");
            return CloudflareResult.ofNotPassed();
        }

        final String targetId;
        try {
            targetId = driver.getWindowHandle();
        } catch (final WebDriverException e) {
            LOGGER.debug("Unable to find the tab of the browser", e);
            return CloudflareResult.ofNotPassed();
        }

        ExecutionCancellation.throwIfCancelled();
        final List<String> command = List.of(PYTHON_EXECUTABLE, SCRIPT_PATH, "--address", debuggerAddress, "--target", targetId,
            "--timeout", String.valueOf(CHALLENGE_SOLVE_TIMEOUT.toSeconds()));
        LOGGER.trace("Running zendriver Cloudflare check: {}", command);

        final Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (final IOException e) {
            LOGGER.warn("Unable to start the zendriver Cloudflare check: {}", e.getMessage());
            LOGGER.debug("Unable to start '{}'", command, e);
            return CloudflareResult.ofNotPassed();
        }

        // Closing the process closes its streams (it has already ended, or been stopped, by then)
        try (process) {
            final Thread errorLogger = logLines(process.getErrorStream());
            if (!process.waitFor(CHALLENGE_SOLVE_TIMEOUT.plus(PROCESS_OVERHEAD).toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.debug("zendriver Cloudflare check did not finish in time, stopping it");
                process.destroyForcibly();
                return CloudflareResult.ofNotPassed();
            }
            errorLogger.join(Duration.ofSeconds(1L));
            return toResult(readOutput(process.getInputStream()));
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while waiting for the zendriver Cloudflare check", e);
            return CloudflareResult.ofNotPassed();
        } catch (final IOException e) {
            LOGGER.debug("Unable to close the zendriver Cloudflare check", e);
            return CloudflareResult.ofNotPassed();
        }
    }

    // The address of the browser's DevTools endpoint (such as 'localhost:41233'), which chromedriver reports in the session capabilities
    private @Nullable String debuggerAddress() {
        final Object chromeOptions = driver.getCapabilities().getCapability(CHROME_OPTIONS_CAPABILITY);
        if (chromeOptions instanceof final Map<?, ?> options && options.get(DEBUGGER_ADDRESS_KEY) instanceof final String address) {
            return address;
        }
        return null;
    }

    // Logs each line of the script's stderr in the background, in the logging context of the tracker
    private static Thread logLines(final InputStream inputStream) {
        final Map<String, String> loggingContext = ThreadContext.getImmutableContext();
        return Thread.ofVirtual().name("zendriver-log").start(() -> {
            ThreadContext.putAll(loggingContext);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    LOGGER.debug("zendriver: {}", line);
                    line = reader.readLine();
                }
            } catch (final IOException e) {
                LOGGER.trace("Stopped reading zendriver output", e);
            } finally {
                ThreadContext.clearMap();
            }
        });
    }

    private static String readOutput(final InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (final IOException e) {
            LOGGER.debug("Unable to read the result of the zendriver Cloudflare check", e);
            return "";
        }
    }

    private static CloudflareResult toResult(final String output) {
        final String lastLine = output.lines().reduce((first, second) -> second).orElse("");
        final JsonObject json;
        try {
            json = JsonParser.parseString(lastLine).getAsJsonObject();
        } catch (final JsonParseException | IllegalStateException e) {
            LOGGER.warn("Unexpected result from the zendriver Cloudflare check: '{}'", lastLine);
            LOGGER.debug("Unable to parse '{}'", lastLine, e);
            return CloudflareResult.ofNotPassed();
        }

        final String result = json.has("result") ? json.get("result").getAsString() : "";
        final String detail = json.has("detail") ? json.get("detail").getAsString() : "";
        return switch (result) {
            case "NOT_PRESENT" -> {
                LOGGER.debug("zendriver found no Cloudflare challenge");
                yield CloudflareResult.ofPassed();
            }
            case "PASSED" -> {
                LOGGER.debug("zendriver passed the Cloudflare challenge");
                yield CloudflareResult.ofPassed();
            }
            case "FAILED" -> {
                LOGGER.debug("zendriver did not pass the Cloudflare challenge: {}", detail);
                yield CloudflareResult.ofNotPassed();
            }
            default -> {
                LOGGER.warn("zendriver Cloudflare check failed: {}", detail);
                yield CloudflareResult.ofNotPassed();
            }
        };
    }
}
