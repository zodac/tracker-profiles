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

package net.zodac.tracker.framework.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BrowserView}, {@link BrowserViews} and {@link BrowserFrame}.
 */
class BrowserViewTest {

    @Test
    void givenInvalidAddressOrTarget_whenForTab_thenExceptionIsThrown() {
        assertThatThrownBy(() -> BrowserView.forTab(" ", "target"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrowserView.forTab("localhost:9222/other", "target"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrowserView.forTab("localhost:9222", "../browser"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenClosedView_whenAwaitFrame_thenNoFrameIsReturned() throws InterruptedException {
        final BrowserView view = BrowserView.forTab("localhost:1", "target");
        view.close();

        assertThat(view.awaitFrame(0L, Duration.ofSeconds(5L)))
            .isEmpty();
        assertThat(view.isClosed())
            .isTrue();
    }

    @Test
    void givenUnreachableBrowser_whenAwaitFrame_thenNoFrameIsReturnedAndViewStaysOpen() throws InterruptedException {
        final BrowserView view = BrowserView.forTab("localhost:1", "target");

        assertThat(view.awaitFrame(0L, Duration.ofMillis(100L)))
            .isEmpty();
        assertThat(view.isClosed())
            .isFalse();
        view.close();
    }

    @Test
    void givenNoFrameShown_whenDispatch_thenExceptionIsThrown() {
        final BrowserView view = BrowserView.forTab("localhost:1", "target");

        assertThatThrownBy(() -> view.dispatch(List.of()))
            .isInstanceOf(IllegalStateException.class);
        view.close();
    }

    @Test
    void givenUnknownTracker_whenFind_thenNoViewIsReturned() {
        assertThat(BrowserViews.find("unknown-tracker"))
            .isEmpty();
    }

    @Test
    void givenFrame_whenWriteTo_thenImageIsWrittenAndNotShared() throws IOException {
        final byte[] jpeg = {1, 2, 3};
        final BrowserFrame frame = BrowserFrame.of(7L, jpeg, 1680, 1050);
        jpeg[0] = 9;

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        frame.writeTo(outputStream);
        assertThat(outputStream.toByteArray())
            .containsExactly(1, 2, 3);
        assertThat(frame.size())
            .isEqualTo(3);
        assertThat(frame.id())
            .isEqualTo(7L);
    }

    @Test
    void givenEmptyImageOrViewport_whenFrameOf_thenExceptionIsThrown() {
        assertThatThrownBy(() -> BrowserFrame.of(1L, new byte[0], 1680, 1050))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrowserFrame.of(1L, new byte[] {1}, 0, 1050))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
