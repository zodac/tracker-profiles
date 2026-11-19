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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BrowserInputEvent}.
 */
class BrowserInputEventTest {

    private static final double VIEWPORT_WIDTH = 1680;
    private static final double VIEWPORT_HEIGHT = 1050;

    @Test
    void givenMousePress_whenToCommand_thenPositionIsScaledToViewport() {
        final DevToolsCommand command = mouse("mousePressed", 0.5, 0.25, "left").toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.method())
            .isEqualTo("Input.dispatchMouseEvent");
        assertThat(command.params())
            .containsEntry("type", "mousePressed")
            .containsEntry("x", 840.0)
            .containsEntry("y", 262.5)
            .containsEntry("button", "left")
            .containsEntry("clickCount", 1);
    }

    @Test
    void givenPositionOutsideView_whenToCommand_thenPositionIsClamped() {
        final DevToolsCommand command = mouse("mouseMoved", -2, 5, null).toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.params())
            .containsEntry("x", 0.0)
            .containsEntry("y", VIEWPORT_HEIGHT)
            .containsEntry("button", "none");
    }

    @Test
    void givenUnknownMouseAction_whenToCommand_thenExceptionIsThrown() {
        final BrowserInputEvent event = mouse("mouseExploded", 0, 0, "left");

        assertThatThrownBy(() -> event.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mouseExploded");
    }

    @Test
    void givenUnknownMouseButton_whenToCommand_thenExceptionIsThrown() {
        final BrowserInputEvent event = mouse("mousePressed", 0, 0, "back");

        assertThatThrownBy(() -> event.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenWheel_whenToCommand_thenScrollIsClamped() {
        final BrowserInputEvent event = new BrowserInputEvent("wheel", null, 0.5, 0.5, null, 0, 0, 50, 1_000_000, null, null, 0, 0, null, 0);

        assertThat(event.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT).params())
            .containsEntry("type", "mouseWheel")
            .containsEntry("deltaX", 50.0)
            .containsEntry("deltaY", 10_000.0);
    }

    @Test
    void givenKeyDownWithText_whenToCommand_thenKeyDownTypesText() {
        final DevToolsCommand command = key("keyDown", "a", "KeyA", 65, "a").toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.method())
            .isEqualTo("Input.dispatchKeyEvent");
        assertThat(command.params())
            .containsEntry("type", "keyDown")
            .containsEntry("key", "a")
            .containsEntry("code", "KeyA")
            .containsEntry("windowsVirtualKeyCode", 65)
            .containsEntry("text", "a");
    }

    @Test
    void givenKeyDownWithoutText_whenToCommand_thenRawKeyDownIsSent() {
        final DevToolsCommand command = key("keyDown", "Backspace", "Backspace", 8, "").toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.params())
            .containsEntry("type", "rawKeyDown")
            .doesNotContainKey("text");
    }

    @Test
    void givenKeyUp_whenToCommand_thenNoTextIsSent() {
        final DevToolsCommand command = key("keyUp", "a", "KeyA", 65, "a").toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.params())
            .containsEntry("type", "keyUp")
            .doesNotContainKey("text");
    }

    @Test
    void givenKeyWithoutKeyValue_whenToCommand_thenExceptionIsThrown() {
        final BrowserInputEvent event = key("keyDown", null, "KeyA", 65, "a");

        assertThatThrownBy(() -> event.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenPastedText_whenToCommand_thenTextIsInserted() {
        final DevToolsCommand command = text("123456").toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

        assertThat(command.method())
            .isEqualTo("Input.insertText");
        assertThat(command.params())
            .containsEntry("text", "123456");
    }

    @Test
    void givenTooLongPastedText_whenToCommand_thenExceptionIsThrown() {
        final BrowserInputEvent event = text("x".repeat(1_001));

        assertThatThrownBy(() -> event.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenUnknownOrMissingType_whenToCommand_thenExceptionIsThrown() {
        final BrowserInputEvent unknown = new BrowserInputEvent("touch", null, 0, 0, null, 0, 0, 0, 0, null, null, 0, 0, null, 0);
        final BrowserInputEvent missing = new BrowserInputEvent(null, null, 0, 0, null, 0, 0, 0, 0, null, null, 0, 0, null, 0);

        assertThatThrownBy(() -> unknown.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> missing.toCommand(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static BrowserInputEvent mouse(final String action, final double x, final double y, final @Nullable String button) {
        return new BrowserInputEvent("mouse", action, x, y, button, 0, 1, 0, 0, null, null, 0, 0, null, 0);
    }

    private static BrowserInputEvent key(final String action, final @Nullable String key, final String code, final int keyCode,
        final String text) {
        return new BrowserInputEvent("key", action, 0, 0, null, 0, 0, 0, 0, key, code, keyCode, 0, text, 0);
    }

    private static BrowserInputEvent text(final String text) {
        return new BrowserInputEvent("text", null, 0, 0, null, 0, 0, 0, 0, null, null, 0, 0, text, 0);
    }
}
