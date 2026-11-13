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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Automatically passes a Cloudflare verification check (either a full-page challenge, or a Turnstile widget embedded in a login form) without any
 * user input.
 *
 * <p>
 * Cloudflare detects automation in several ways that matter here, all of which are avoided:
 *
 * <ul>
 *     <li>JavaScript executed in the page that inspects the challenge elements. The challenge is therefore only ever located through the Chrome
 *         DevTools Protocol {@code DOM} domain (which also pierces the closed shadow root holding the challenge {@code iframe}), and the state of
 *         the challenge is read from an isolated JavaScript world that the page cannot observe.</li>
 *     <li>Synthetic clicks dispatched through the DOM. The checkbox is instead clicked with a browser-level mouse event through the Chrome
 *         DevTools Protocol {@code Input} domain, which is indistinguishable from a real click and does not require the browser window to be
 *         visible or focused, so it works in a headless browser.</li>
 *     <li>The variables that {@code chromedriver} adds to every page. These cannot be hidden from here, so they are removed from the
 *         {@code chromedriver} binary when the Docker image is built. Without this, stricter websites reject the click.</li>
 * </ul>
 *
 * <p>
 * The challenge announces its progress to the page it is embedded in (when it is ready to be clicked, when it has accepted a click, and when it
 * has passed or failed). These announcements are recorded from the isolated world, so that the checkbox is clicked as soon as the challenge asks
 * for it, is not clicked again once a click has been accepted, and is never clicked after the challenge has failed (a failed challenge replaces
 * the checkbox with a link to Cloudflare's troubleshooting page).
 *
 * <p>
 * This is based on the technique used by {@code zendriver}, implemented on top of the existing {@link RemoteWebDriver}. Neither it nor any
 * other library is a dependency.
 */
public final class CloudflareSolver {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CHALLENGE_DOMAIN = "challenges.cloudflare.com";
    private static final String IFRAME_NODE_NAME = "IFRAME";
    private static final String ISOLATED_WORLD_NAME = "tracker-profiles";

    // How long to wait for a challenge to appear before assuming that Cloudflare has not presented one
    private static final Duration CHALLENGE_APPEARANCE_TIMEOUT = Duration.ofSeconds(10L);
    // How long to wait for the challenge to be passed, in total
    private static final Duration CHALLENGE_SOLVE_TIMEOUT = Duration.ofSeconds(45L);
    // If the challenge was not seen asking for a click, the checkbox must be visible for this long before it is clicked. A challenge will often
    // pass by itself without interaction, and ignores any click made before it is ready.
    private static final Duration CHECKBOX_SETTLE_DURATION = Duration.ofSeconds(4L);
    // How long to wait for a click to be acknowledged before clicking again
    private static final Duration CLICK_INTERVAL = Duration.ofSeconds(6L);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500L);
    private static final int MAXIMUM_CLICKS = 3;
    // A full-page challenge reloads the page when passed, so it must be missing for multiple polls to avoid mistaking the reload for a pass
    private static final int ABSENT_POLLS_REQUIRED = 4;
    // A page can hold several challenges that do not all become ready at the same moment, and only the progress of all of them combined is
    // known. The request for a click must therefore be seen on multiple polls, giving the visible challenge time to become ready too.
    private static final int AWAITING_CLICK_POLLS_REQUIRED = 2;

    // The checkbox is at the left of the challenge, vertically centred
    private static final double CHECKBOX_HORIZONTAL_RATIO = 0.15d;
    private static final double CHECKBOX_VERTICAL_RATIO = 0.5d;
    private static final double MINIMUM_CHALLENGE_SIZE_IN_PIXELS = 10.0d;
    // A real mouse reports a pressure of 0.5 while a button is pressed
    private static final double MOUSE_PRESSED_FORCE = 0.5d;

    // Executed in an isolated world, so the page cannot observe the query or the listener. The first execution in a world starts recording the
    // progress announced by the challenge, and every execution returns the current state. The token itself is never returned.
    private static final String CHALLENGE_STATE_SCRIPT = """
        (() => {
            if (!globalThis.challengeProgress) {
                const progress = {awaitingClick: false, clickAccepted: false, errorCode: null};
                globalThis.challengeProgress = progress;
                window.addEventListener('message', (event) => {
                    const data = event.data;
                    if (event.origin !== 'https://challenges.cloudflare.com' || !data || data.source !== 'cloudflare-challenge') {
                        return;
                    }
                    if (data.event === 'interactiveBegin') {
                        progress.awaitingClick = true;
                    } else if (data.event === 'interactiveEnd') {
                        progress.awaitingClick = false;
                        progress.clickAccepted = true;
                    } else if (data.event === 'fail') {
                        progress.errorCode = String(data.code || 'unknown');
                    }
                });
            }

            const inputs = [...document.querySelectorAll('input[name="cf-turnstile-response"], input[name="cf_challenge_response"]')];
            const response = inputs.length === 0 ? 'ABSENT' : (inputs.some((input) => input.value) ? 'PASSED' : 'PENDING');
            return {...globalThis.challengeProgress, response: response};
        })()
        """;

    private final RemoteWebDriver driver;

    private CloudflareSolver(final RemoteWebDriver driver) {
        this.driver = driver;
    }

    /**
     * Creates a {@link CloudflareSolver} for the provided {@link RemoteWebDriver}.
     *
     * @param driver the {@link RemoteWebDriver} currently showing the Cloudflare verification check
     * @return the {@link CloudflareSolver}
     */
    public static CloudflareSolver forDriver(final RemoteWebDriver driver) {
        return new CloudflareSolver(driver);
    }

    /**
     * Attempts to pass the Cloudflare verification check on the current page.
     *
     * <p>
     * The page is polled until one of the following occurs:
     *
     * <ul>
     *     <li>The challenge reports that it has been passed</li>
     *     <li>A challenge that was shown is removed from the page (a full-page challenge redirecting to the tracker)</li>
     *     <li>No challenge appears at all, in which case there is nothing to pass</li>
     *     <li>The challenge reports that it has failed, in which case the attempt ends immediately with Cloudflare's error code</li>
     *     <li>The challenge is still not passed after the maximum wait or number of clicks, in which case the attempt has failed</li>
     * </ul>
     *
     * <p>
     * The checkbox is clicked when the challenge asks for it. If the challenge was not seen asking (it may have asked before it was being
     * observed), the checkbox is clicked once it has been visible for a short time. No click is made once a click has been accepted or the
     * challenge has failed.
     *
     * @return the {@link CloudflareResult} of the attempt
     */
    public CloudflareResult solve() {
        if (!(driver instanceof final HasCdp cdp)) {
            LOGGER.debug("\t\t- Browser does not support the Chrome DevTools Protocol, unable to automatically pass Cloudflare verification");
            return CloudflareResult.ofNotPassed();
        }

        final Progress progress = new Progress();
        while (elapsedSince(progress.start).compareTo(CHALLENGE_SOLVE_TIMEOUT) < 0) {
            final CloudflareResult result = poll(cdp, progress);
            if (result != null) {
                LOGGER.trace("Cloudflare challenge finished with {} after {} click(s)", result, progress.clicks);
                return result;
            }

            BrowserInteractionHelper.explicitWait(POLL_INTERVAL, "Cloudflare verification to progress");
        }

        LOGGER.trace("Timed out after {} waiting for Cloudflare challenge to pass", CHALLENGE_SOLVE_TIMEOUT);
        return CloudflareResult.ofNotPassed();
    }

    // Returns the result once the attempt has finished, or null while it is still in progress
    private static @Nullable CloudflareResult poll(final HasCdp cdp, final Progress progress) {
        final Snapshot snapshot = inspect(cdp, progress);
        if (snapshot == null) {
            LOGGER.trace("Unable to inspect page (it may be navigating), trying again");
            return null;
        }

        final PageState pageState = snapshot.pageState();
        if (pageState.errorCode() != null) {
            LOGGER.debug("\t\t- Cloudflare rejected the verification with error code {}", pageState.errorCode());
            return CloudflareResult.ofRejected(pageState.errorCode());
        }

        if (pageState.response() == ChallengeState.PASSED) {
            return CloudflareResult.ofPassed();
        }

        if (!snapshot.isChallengePresent()) {
            return progress.onChallengeAbsent();
        }

        final Point clickPoint = snapshot.clickPoint();
        if (!progress.onChallengePresent(clickPoint, pageState) || clickPoint == null) {
            return null;
        }

        if (progress.clicks >= MAXIMUM_CLICKS) {
            return CloudflareResult.ofNotPassed();
        }

        click(cdp, clickPoint);
        progress.onClick();
        return null;
    }

    private static @Nullable Snapshot inspect(final HasCdp cdp, final Progress progress) {
        try {
            final Map<String, Object> document = cdp.executeCdpCommand("DOM.getDocument", Map.of("depth", -1, "pierce", true));
            final Optional<Long> challengeFrame = findChallengeFrame(asMap(document.get("root")));
            final Point clickPoint = challengeFrame.map(backendNodeId -> locateCheckbox(cdp, backendNodeId)).orElse(null);
            return new Snapshot(readPageState(cdp, progress), challengeFrame.isPresent(), clickPoint);
        } catch (final WebDriverException e) {
            LOGGER.trace("Error inspecting page for Cloudflare challenge", e);
            return null;
        }
    }

    // The isolated world is reused between polls, since it holds the progress recorded so far. It is destroyed when the page navigates, at which
    // point a new one is created for the new page.
    private static PageState readPageState(final HasCdp cdp, final Progress progress) {
        if (progress.isolatedWorld != null) {
            try {
                return evaluateChallengeState(cdp, progress.isolatedWorld);
            } catch (final WebDriverException e) {
                LOGGER.trace("Isolated world no longer exists, creating a new one", e);
            }
        }

        progress.isolatedWorld = createIsolatedWorld(cdp);
        return progress.isolatedWorld == null ? PageState.EMPTY : evaluateChallengeState(cdp, progress.isolatedWorld);
    }

    private static @Nullable Object createIsolatedWorld(final HasCdp cdp) {
        final Map<?, ?> frameTree = asMap(cdp.executeCdpCommand("Page.getFrameTree", Map.of()).get("frameTree"));
        final Object frameId = asMap(frameTree.get("frame")).get("id");
        if (frameId == null) {
            return null;
        }

        return cdp
            .executeCdpCommand("Page.createIsolatedWorld", Map.of("frameId", frameId, "worldName", ISOLATED_WORLD_NAME))
            .get("executionContextId");
    }

    private static PageState evaluateChallengeState(final HasCdp cdp, final Object isolatedWorld) {
        final Map<String, Object> evaluation = cdp.executeCdpCommand("Runtime.evaluate",
            Map.of("expression", CHALLENGE_STATE_SCRIPT, "contextId", isolatedWorld, "returnByValue", true));
        if (evaluation.containsKey("exceptionDetails")) {
            throw new WebDriverException("Unable to read the state of the Cloudflare challenge");
        }
        return PageState.parse(asMap(asMap(evaluation.get("result")).get("value")));
    }

    private static @Nullable Point locateCheckbox(final HasCdp cdp, final long backendNodeId) {
        try {
            cdp.executeCdpCommand("DOM.scrollIntoViewIfNeeded", Map.of("backendNodeId", backendNodeId));
            final Map<String, Object> boxModel = cdp.executeCdpCommand("DOM.getBoxModel", Map.of("backendNodeId", backendNodeId));
            return checkboxPosition(asList(asMap(boxModel.get("model")).get("content"))).orElse(null);
        } catch (final WebDriverException e) {
            // Thrown when the element has no layout, which is the case while the challenge is hidden
            LOGGER.trace("Cloudflare challenge is not visible", e);
            return null;
        }
    }

    private static void click(final HasCdp cdp, final Point point) {
        LOGGER.debug("\t\t- Clicking Cloudflare verification checkbox");
        LOGGER.trace("Clicking at ({}, {})", point.x(), point.y());
        cdp.executeCdpCommand("Input.dispatchMouseEvent", Map.of(
            "type", "mousePressed",
            "x", point.x(),
            "y", point.y(),
            "button", "left",
            "buttons", 1,
            "clickCount", 1,
            "force", MOUSE_PRESSED_FORCE
        ));
        cdp.executeCdpCommand("Input.dispatchMouseEvent", Map.of(
            "type", "mouseReleased",
            "x", point.x(),
            "y", point.y(),
            "button", "left",
            "buttons", 0,
            "clickCount", 1
        ));
    }

    /**
     * Searches a DOM node (as returned by the Chrome DevTools Protocol command {@code DOM.getDocument}) and all of its descendants for the
     * {@code iframe} hosting a Cloudflare challenge. Shadow roots and nested documents are included in the search, since Cloudflare places the
     * {@code iframe} inside a closed shadow root.
     *
     * @param node the DOM node to search
     * @return the backend node ID of the Cloudflare challenge {@code iframe}, if one exists
     */
    static Optional<Long> findChallengeFrame(final Map<?, ?> node) {
        if (IFRAME_NODE_NAME.equals(node.get("nodeName")) && hasChallengeSource(asList(node.get("attributes")))
            && node.get("backendNodeId") instanceof final Number backendNodeId) {
            return Optional.of(backendNodeId.longValue());
        }

        for (final String childrenKey : List.of("children", "shadowRoots")) {
            for (final Object child : asList(node.get(childrenKey))) {
                final Optional<Long> challengeFrame = findChallengeFrame(asMap(child));
                if (challengeFrame.isPresent()) {
                    return challengeFrame;
                }
            }
        }

        final Map<?, ?> contentDocument = asMap(node.get("contentDocument"));
        return contentDocument.isEmpty() ? Optional.empty() : findChallengeFrame(contentDocument);
    }

    /**
     * Calculates where the checkbox is within a Cloudflare challenge, given the position of the challenge on the page.
     *
     * @param contentQuad the corners of the challenge as returned by the Chrome DevTools Protocol command {@code DOM.getBoxModel}, in the format
     *                    {@code [x1, y1, x2, y2, x3, y3, x4, y4]}
     * @return the {@link Point} to click, or {@link Optional#empty()} if the challenge is not large enough to be visible
     */
    static Optional<Point> checkboxPosition(final Collection<?> contentQuad) {
        final List<Double> coordinates = contentQuad.stream()
            .filter(Number.class::isInstance)
            .map(coordinate -> ((Number) coordinate).doubleValue())
            .toList();
        if (coordinates.size() != 8 || coordinates.size() != contentQuad.size()) {
            return Optional.empty();
        }

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < coordinates.size(); i += 2) {
            minX = Math.min(minX, coordinates.get(i));
            maxX = Math.max(maxX, coordinates.get(i));
            minY = Math.min(minY, coordinates.get(i + 1));
            maxY = Math.max(maxY, coordinates.get(i + 1));
        }

        final double width = maxX - minX;
        final double height = maxY - minY;
        if (width < MINIMUM_CHALLENGE_SIZE_IN_PIXELS || height < MINIMUM_CHALLENGE_SIZE_IN_PIXELS) {
            return Optional.empty();
        }

        return Optional.of(new Point(minX + width * CHECKBOX_HORIZONTAL_RATIO, minY + height * CHECKBOX_VERTICAL_RATIO));
    }

    // Attributes are returned as a flat list of alternating names and values
    private static boolean hasChallengeSource(final List<?> attributes) {
        for (int i = 0; i + 1 < attributes.size(); i += 2) {
            if ("src".equals(attributes.get(i)) && String.valueOf(attributes.get(i + 1)).contains(CHALLENGE_DOMAIN)) {
                return true;
            }
        }
        return false;
    }

    private static Duration elapsedSince(final long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static Map<?, ?> asMap(final @Nullable Object value) {
        return value instanceof final Map<?, ?> map ? map : Map.of();
    }

    private static List<?> asList(final @Nullable Object value) {
        return value instanceof final List<?> list ? list : List.of();
    }

    /**
     * A position on the page, in CSS pixels relative to the viewport.
     *
     * @param x the horizontal position
     * @param y the vertical position
     */
    record Point(double x, double y) {

    }

    /**
     * The state of the Cloudflare challenge, as reported by the response field that Cloudflare adds to the page.
     */
    enum ChallengeState {

        /**
         * There is no response field, so the challenge cannot report its state.
         */
        ABSENT,

        /**
         * The challenge has not yet been passed.
         */
        PENDING,

        /**
         * The challenge has been passed.
         */
        PASSED;

        /**
         * Converts the value returned by the page into a {@link ChallengeState}.
         *
         * @param value the value returned by the page
         * @return the matching {@link ChallengeState}, or {@link #ABSENT} if the value is not recognised
         */
        static ChallengeState parse(final @Nullable Object value) {
            for (final ChallengeState challengeState : values()) {
                if (challengeState.name().equals(value)) {
                    return challengeState;
                }
            }
            return ABSENT;
        }
    }

    /**
     * The state of the Cloudflare challenge, as read from the page.
     *
     * @param response      the {@link ChallengeState} reported by the response field
     * @param awaitingClick whether the challenge has asked to be clicked, and has not yet accepted a click
     * @param clickAccepted whether the challenge has accepted a click
     * @param errorCode     the error code reported by the challenge if it has failed, otherwise {@code null}
     */
    record PageState(ChallengeState response, boolean awaitingClick, boolean clickAccepted, @Nullable String errorCode) {

        private static final PageState EMPTY = new PageState(ChallengeState.ABSENT, false, false, null);

        /**
         * Converts the value returned by the page into a {@link PageState}. Any missing or unrecognised value is treated as not having happened.
         *
         * @param value the value returned by the page
         * @return the {@link PageState}
         */
        static PageState parse(final Map<?, ?> value) {
            final String errorCode = value.get("errorCode") instanceof final String code && !code.isBlank() ? code : null;
            return new PageState(
                ChallengeState.parse(value.get("response")),
                Boolean.TRUE.equals(value.get("awaitingClick")),
                Boolean.TRUE.equals(value.get("clickAccepted")),
                errorCode
            );
        }
    }

    private record Snapshot(PageState pageState, boolean hasChallengeFrame, @Nullable Point clickPoint) {

        boolean isChallengePresent() {
            return hasChallengeFrame || pageState.response() == ChallengeState.PENDING;
        }
    }

    // Tracks what has happened to the challenge across polls of a single attempt to pass it
    private static final class Progress {

        private static final long NOT_SET = -1L;

        private final long start = System.nanoTime();
        private boolean challengeSeen;
        private int absentPolls;
        private int clicks;
        private long checkboxFirstSeen = NOT_SET;
        private long lastClick = NOT_SET;
        private int awaitingClickPolls;
        private @Nullable Object isolatedWorld;

        // Returns the result if the absence of the challenge means the attempt has finished, otherwise null
        @Nullable
        CloudflareResult onChallengeAbsent() {
            absentPolls++;
            if (challengeSeen) {
                return absentPolls >= ABSENT_POLLS_REQUIRED ? CloudflareResult.ofPassed() : null;
            }

            if (elapsedSince(start).compareTo(CHALLENGE_APPEARANCE_TIMEOUT) >= 0) {
                LOGGER.debug("\t\t- No Cloudflare verification check was presented");
                return CloudflareResult.ofPassed();
            }
            return null;
        }

        // Returns whether the checkbox should now be clicked
        boolean onChallengePresent(final @Nullable Point clickPoint, final PageState pageState) {
            challengeSeen = true;
            absentPolls = 0;

            if (clickPoint == null) {
                checkboxFirstSeen = NOT_SET;
                return false;
            }

            if (checkboxFirstSeen == NOT_SET) {
                checkboxFirstSeen = System.nanoTime();
            }

            final boolean isClickDue = lastClick == NOT_SET || elapsedSince(lastClick).compareTo(CLICK_INTERVAL) >= 0;
            awaitingClickPolls = pageState.awaitingClick() ? awaitingClickPolls + 1 : 0;
            if (pageState.awaitingClick()) {
                return isClickDue && awaitingClickPolls >= AWAITING_CLICK_POLLS_REQUIRED;
            }

            // Once a click has been accepted the challenge is verifying it, and must be left alone
            final boolean hasSettled = elapsedSince(checkboxFirstSeen).compareTo(CHECKBOX_SETTLE_DURATION) >= 0;
            return !pageState.clickAccepted() && hasSettled && isClickDue;
        }

        void onClick() {
            clicks++;
            lastClick = System.nanoTime();
        }
    }
}
