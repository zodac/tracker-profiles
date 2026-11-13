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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zodac.tracker.framework.cloudflare.CloudflareSolver.ChallengeState;
import net.zodac.tracker.framework.cloudflare.CloudflareSolver.PageState;
import net.zodac.tracker.framework.cloudflare.CloudflareSolver.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Unit tests for {@link CloudflareSolver}.
 */
class CloudflareSolverTest {

    private static final String CHALLENGE_URL = "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile/if/ov2/av0/rcv/1234/";
    private static final long CHALLENGE_BACKEND_NODE_ID = 42L;

    @Test
    void givenFrameInsideShadowRoot_whenFindChallengeFrame_thenBackendNodeIdIsReturned() {
        final Map<String, Object> shadowRoot = node("#document-fragment", List.of(), Map.of("children", List.of(challengeFrame())));
        final Map<String, Object> widget = node("DIV", List.of("class", "cf-turnstile"), Map.of("shadowRoots", List.of(shadowRoot)));
        final Map<String, Object> html = node("HTML", List.of(), Map.of("children", List.of(widget)));
        final Map<String, Object> document = node("#document", List.of(), Map.of("children", List.of(html)));

        final Optional<Long> actual = CloudflareSolver.findChallengeFrame(document);

        assertThat(actual)
            .contains(CHALLENGE_BACKEND_NODE_ID);
    }

    @Test
    void givenFrameInsideNestedDocument_whenFindChallengeFrame_thenBackendNodeIdIsReturned() {
        final Map<String, Object> nestedDocument = node("#document", List.of(), Map.of("children", List.of(challengeFrame())));
        final Map<String, Object> loginFrame = node("IFRAME", List.of("src", "https://example.com/login"), Map.of("contentDocument", nestedDocument));
        final Map<String, Object> document = node("#document", List.of(), Map.of("children", List.of(loginFrame)));

        final Optional<Long> actual = CloudflareSolver.findChallengeFrame(document);

        assertThat(actual)
            .contains(CHALLENGE_BACKEND_NODE_ID);
    }

    @Test
    void givenNoCloudflareFrame_whenFindChallengeFrame_thenEmptyIsReturned() {
        final Map<String, Object> otherFrame = node("IFRAME", List.of("src", "https://example.com/advert"), Map.of());
        final Map<String, Object> link = node("A", List.of("src", CHALLENGE_URL), Map.of());
        final Map<String, Object> frameWithoutSource = node("IFRAME", List.of("title", CHALLENGE_URL), Map.of());
        final Map<String, Object> document = node("#document", List.of(), Map.of("children", List.of(otherFrame, link, frameWithoutSource)));

        final Optional<Long> actual = CloudflareSolver.findChallengeFrame(document);

        assertThat(actual)
            .isEmpty();
    }

    @Test
    void givenVisibleChallenge_whenCheckboxPosition_thenPointAtLeftAndVerticallyCentredIsReturned() {
        final List<Object> contentQuad = List.of(392, 304, 692, 304, 692, 369, 392, 369);

        final Optional<Point> actual = CloudflareSolver.checkboxPosition(contentQuad);

        assertThat(actual)
            .contains(new Point(437.0d, 336.5d));
    }

    @Test
    void givenChallengeWithNoSize_whenCheckboxPosition_thenEmptyIsReturned() {
        final List<Object> contentQuad = List.of(392, 304, 392, 304, 392, 304, 392, 304);

        final Optional<Point> actual = CloudflareSolver.checkboxPosition(contentQuad);

        assertThat(actual)
            .isEmpty();
    }

    @Test
    void givenInvalidQuad_whenCheckboxPosition_thenEmptyIsReturned() {
        assertThat(CloudflareSolver.checkboxPosition(List.of()))
            .isEmpty();
        assertThat(CloudflareSolver.checkboxPosition(List.of(392, 304, 692, 304)))
            .isEmpty();
        assertThat(CloudflareSolver.checkboxPosition(List.of(392, 304, 692, 304, 692, 369, 392, "invalid")))
            .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "ABSENT, ABSENT",
        "PENDING, PENDING",
        "PASSED, PASSED",
        "passed, ABSENT",
        "invalid, ABSENT",
        "null, ABSENT",
    })
    void givenValue_whenParseChallengeState_thenExpectedStateIsReturned(final String input, final ChallengeState expected) {
        final ChallengeState actual = ChallengeState.parse(input);

        assertThat(actual)
            .isEqualTo(expected);
    }

    @Test
    void givenCompleteValue_whenParsePageState_thenAllFieldsAreRead() {
        final PageState actual = PageState.parse(Map.of("response", "PENDING", "awaitingClick", true, "clickAccepted", true, "errorCode", "600010"));

        assertThat(actual)
            .isEqualTo(new PageState(ChallengeState.PENDING, true, true, "600010"));
    }

    @Test
    void givenEmptyValue_whenParsePageState_thenNothingIsReportedAsHavingHappened() {
        final PageState actual = PageState.parse(Map.of());

        assertThat(actual)
            .isEqualTo(new PageState(ChallengeState.ABSENT, false, false, null));
    }

    @Test
    void givenDriverWithoutChromeDevToolsProtocol_whenSolve_thenNotPassedIsReturned() {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class);

        final CloudflareResult actual = CloudflareSolver.forDriver(driver).solve();

        assertThat(actual)
            .isEqualTo(CloudflareResult.ofNotPassed());
    }

    @Test
    void givenChallengeAlreadyPassed_whenSolve_thenPassedIsReturnedWithoutClicking() {
        final RemoteWebDriver driver = driverShowingChallenge(Map.of("response", "PASSED"));

        final CloudflareResult actual = CloudflareSolver.forDriver(driver).solve();

        assertThat(actual)
            .isEqualTo(CloudflareResult.ofPassed());
        verify((HasCdp) driver, never()).executeCdpCommand(eq("Input.dispatchMouseEvent"), anyMap());
    }

    @Test
    void givenChallengeHasFailed_whenSolve_thenRejectedIsReturnedWithoutClicking() {
        final RemoteWebDriver driver = driverShowingChallenge(Map.of("response", "PENDING", "awaitingClick", true, "errorCode", "600010"));

        final CloudflareResult actual = CloudflareSolver.forDriver(driver).solve();

        assertThat(actual)
            .isEqualTo(CloudflareResult.ofRejected("600010"));
        assertThat(actual.wasRejected())
            .isTrue();
        verify((HasCdp) driver, never()).executeCdpCommand(eq("Input.dispatchMouseEvent"), anyMap());
    }

    @Test
    void givenChallengeAwaitingClick_whenSolve_thenCheckboxIsClickedAndResultIsReturnedOncePassed() {
        final RemoteWebDriver driver = driverShowingChallenge(Map.of("response", "PENDING", "awaitingClick", true));
        final HasCdp cdp = (HasCdp) driver;
        when(cdp.executeCdpCommand(eq("Runtime.evaluate"), anyMap()))
            .thenReturn(Map.of("result", Map.of("value", Map.of("response", "PENDING", "awaitingClick", true))))
            .thenReturn(Map.of("result", Map.of("value", Map.of("response", "PENDING", "awaitingClick", true))))
            .thenReturn(Map.of("result", Map.of("value", Map.of("response", "PASSED", "clickAccepted", true))));

        final CloudflareResult actual = CloudflareSolver.forDriver(driver).solve();

        assertThat(actual)
            .isEqualTo(CloudflareResult.ofPassed());
        verify(cdp, times(2)).executeCdpCommand(eq("Input.dispatchMouseEvent"), anyMap());
    }

    private static RemoteWebDriver driverShowingChallenge(final Map<String, Object> pageState) {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class, withSettings().extraInterfaces(HasCdp.class));
        final HasCdp cdp = (HasCdp) driver;
        when(cdp.executeCdpCommand(eq("DOM.getDocument"), anyMap()))
            .thenReturn(Map.of("root", node("#document", List.of(), Map.of("children", List.of(challengeFrame())))));
        when(cdp.executeCdpCommand(eq("DOM.scrollIntoViewIfNeeded"), anyMap()))
            .thenReturn(Map.of());
        when(cdp.executeCdpCommand(eq("DOM.getBoxModel"), anyMap()))
            .thenReturn(Map.of("model", Map.of("content", List.of(392, 304, 692, 304, 692, 369, 392, 369))));
        when(cdp.executeCdpCommand(eq("Page.getFrameTree"), anyMap()))
            .thenReturn(Map.of("frameTree", Map.of("frame", Map.of("id", "frame-id"))));
        when(cdp.executeCdpCommand(eq("Page.createIsolatedWorld"), anyMap()))
            .thenReturn(Map.of("executionContextId", 7L));
        when(cdp.executeCdpCommand(eq("Runtime.evaluate"), anyMap()))
            .thenReturn(Map.of("result", Map.of("value", pageState)));
        when(cdp.executeCdpCommand(eq("Input.dispatchMouseEvent"), anyMap()))
            .thenReturn(Map.of());
        return driver;
    }

    private static Map<String, Object> challengeFrame() {
        return node("IFRAME", List.of("id", "cf-chl-widget", "src", CHALLENGE_URL), Map.of("backendNodeId", CHALLENGE_BACKEND_NODE_ID));
    }

    private static Map<String, Object> node(final String nodeName, final List<String> attributes, final Map<String, Object> additionalFields) {
        final Map<String, Object> node = new HashMap<>(additionalFields);
        node.put("nodeName", nodeName);
        node.put("attributes", attributes);
        return node;
    }
}
