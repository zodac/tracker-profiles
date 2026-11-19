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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An input from the user in the live browser view of the web UI, converted into a Chrome DevTools Protocol {@code Input} command by
 * {@link #toCommand(double, double)}. Positions are relative to the view ({@code 0} to {@code 1} across its width and height), so they do not
 * depend on the size at which the web UI shows it.
 *
 * <p>
 * Every value comes from the web UI, so each is validated (or clamped to its range) before it is sent to the browser.
 *
 * @param type       the kind of input: {@code mouse}, {@code wheel}, {@code key} or {@code text} (text pasted by the user)
 * @param action     for {@code mouse}, {@code mousePressed}, {@code mouseReleased} or {@code mouseMoved}; for {@code key}, {@code keyDown} or
 *                   {@code keyUp}
 * @param x          the horizontal position, from {@code 0} (left) to {@code 1} (right), for {@code mouse} and {@code wheel}
 * @param y          the vertical position, from {@code 0} (top) to {@code 1} (bottom), for {@code mouse} and {@code wheel}
 * @param button     the mouse button pressed or released ({@code none}, {@code left}, {@code middle} or {@code right}), for {@code mouse}
 * @param buttons    the mouse buttons held, as a bit mask (left {@code 1}, right {@code 2}, middle {@code 4}), for {@code mouse}
 * @param clickCount the number of consecutive clicks, for {@code mouse}
 * @param deltaX     the horizontal scroll, in CSS pixels, for {@code wheel}
 * @param deltaY     the vertical scroll, in CSS pixels, for {@code wheel}
 * @param key        the key value (such as {@code a} or {@code Enter}), for {@code key}
 * @param code       the physical key (such as {@code KeyA}), for {@code key}
 * @param keyCode    the legacy key code (such as {@code 65}), for {@code key}
 * @param location   the location of the key on the keyboard, for {@code key}
 * @param text       the text typed by a key, for {@code key}, or the pasted text, for {@code text}
 * @param modifiers  the modifier keys held, as a bit mask (Alt {@code 1}, Ctrl {@code 2}, Meta {@code 4}, Shift {@code 8})
 */
public record BrowserInputEvent(@Nullable String type, @Nullable String action, double x, double y, @Nullable String button, int buttons,
    int clickCount, double deltaX, double deltaY, @Nullable String key, @Nullable String code, int keyCode, int location, @Nullable String text,
    int modifiers) {

    private static final Set<String> MOUSE_ACTIONS = Set.of("mousePressed", "mouseReleased", "mouseMoved");
    private static final Set<String> MOUSE_BUTTONS = Set.of("none", "left", "middle", "right");
    private static final Set<String> KEY_ACTIONS = Set.of("keyDown", "keyUp");
    private static final int MAXIMUM_BUTTONS = 7;
    private static final int MAXIMUM_CLICK_COUNT = 3;
    private static final int MAXIMUM_MODIFIERS = 15;
    private static final int MAXIMUM_KEY_CODE = 255;
    private static final int MAXIMUM_LOCATION = 3;
    private static final double MAXIMUM_SCROLL = 10_000;
    private static final int MAXIMUM_KEY_LENGTH = 32;
    private static final int MAXIMUM_KEY_TEXT_LENGTH = 4;
    private static final int MAXIMUM_PASTED_TEXT_LENGTH = 1_000;

    /**
     * Converts the input into the Chrome DevTools Protocol command that sends it to the browser.
     *
     * @param viewportWidth  the width of the browser's viewport, in CSS pixels
     * @param viewportHeight the height of the browser's viewport, in CSS pixels
     * @return the {@link DevToolsCommand}
     * @throws IllegalArgumentException thrown if the input is not valid
     */
    public DevToolsCommand toCommand(final double viewportWidth, final double viewportHeight) {
        if (type == null) {
            throw new IllegalArgumentException("Input type must be provided");
        }

        return switch (type) {
            case "mouse" -> mouseCommand(viewportWidth, viewportHeight);
            case "wheel" -> wheelCommand(viewportWidth, viewportHeight);
            case "key" -> keyCommand();
            case "text" -> textCommand();
            default -> throw new IllegalArgumentException("Unknown input type: '%s'".formatted(type));
        };
    }

    private DevToolsCommand mouseCommand(final double viewportWidth, final double viewportHeight) {
        final String mouseAction = allowed(action, MOUSE_ACTIONS, "mouse action");
        final String mouseButton = button == null ? "none" : allowed(button, MOUSE_BUTTONS, "mouse button");
        final Map<String, Object> params = position(viewportWidth, viewportHeight);
        params.put("type", mouseAction);
        params.put("button", mouseButton);
        params.put("buttons", clamp(buttons, MAXIMUM_BUTTONS));
        params.put("clickCount", clamp(clickCount, MAXIMUM_CLICK_COUNT));
        params.put("modifiers", clamp(modifiers, MAXIMUM_MODIFIERS));
        return new DevToolsCommand("Input.dispatchMouseEvent", params);
    }

    private DevToolsCommand wheelCommand(final double viewportWidth, final double viewportHeight) {
        final Map<String, Object> params = position(viewportWidth, viewportHeight);
        params.put("type", "mouseWheel");
        params.put("deltaX", clamp(deltaX, -MAXIMUM_SCROLL, MAXIMUM_SCROLL));
        params.put("deltaY", clamp(deltaY, -MAXIMUM_SCROLL, MAXIMUM_SCROLL));
        params.put("modifiers", clamp(modifiers, MAXIMUM_MODIFIERS));
        return new DevToolsCommand("Input.dispatchMouseEvent", params);
    }

    // A key that types text is a 'keyDown' (which also types the text), any other is a 'rawKeyDown', as sent by Chrome for a real key press
    private DevToolsCommand keyCommand() {
        final String keyAction = allowed(action, KEY_ACTIONS, "key action");
        if (key == null || key.isEmpty() || key.length() > MAXIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("Key must be between 1 and %d characters".formatted(MAXIMUM_KEY_LENGTH));
        }
        if (code != null && code.length() > MAXIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("Key code must be at most %d characters".formatted(MAXIMUM_KEY_LENGTH));
        }
        if (text != null && text.length() > MAXIMUM_KEY_TEXT_LENGTH) {
            throw new IllegalArgumentException("Key text must be at most %d characters".formatted(MAXIMUM_KEY_TEXT_LENGTH));
        }

        final boolean typesText = "keyDown".equals(keyAction) && text != null && !text.isEmpty();
        final Map<String, Object> params = new HashMap<>();
        params.put("type", "keyUp".equals(keyAction) ? "keyUp" : (typesText ? "keyDown" : "rawKeyDown"));
        params.put("key", key);
        params.put("code", code == null ? "" : code);
        params.put("windowsVirtualKeyCode", clamp(keyCode, MAXIMUM_KEY_CODE));
        params.put("location", clamp(location, MAXIMUM_LOCATION));
        params.put("modifiers", clamp(modifiers, MAXIMUM_MODIFIERS));
        if (typesText) {
            params.put("text", text);
            params.put("unmodifiedText", text);
        }
        return new DevToolsCommand("Input.dispatchKeyEvent", params);
    }

    private DevToolsCommand textCommand() {
        if (text == null || text.isEmpty() || text.length() > MAXIMUM_PASTED_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text must be between 1 and %d characters".formatted(MAXIMUM_PASTED_TEXT_LENGTH));
        }
        final Map<String, Object> params = new HashMap<>();
        params.put("text", text);
        return new DevToolsCommand("Input.insertText", params);
    }

    private Map<String, Object> position(final double viewportWidth, final double viewportHeight) {
        final Map<String, Object> params = new HashMap<>();
        params.put("x", clamp(x, 0, 1) * viewportWidth);
        params.put("y", clamp(y, 0, 1) * viewportHeight);
        return params;
    }

    private static String allowed(final @Nullable String value, final Set<String> allowedValues, final String description) {
        if (value == null || !allowedValues.contains(value)) {
            throw new IllegalArgumentException("Unknown %s: '%s'".formatted(description, value));
        }
        return value;
    }

    private static int clamp(final int value, final int maximum) {
        return Math.clamp(value, 0, maximum);
    }

    // Not a number (which Gson cannot produce from JSON, but is checked anyway) is treated as 0
    private static double clamp(final double value, final double minimum, final double maximum) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.clamp(value, minimum, maximum);
    }
}
