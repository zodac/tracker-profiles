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

package net.zodac.tracker.handler;

import net.zodac.tracker.framework.annotation.TrackerHandler;

/**
 * Extension of the {@link Unit3dHandler} for the {@code RetroMoviesClub} tracker.
 */
@TrackerHandler(name = "RetroMoviesClub", url = "https://retro-movies.club/")
public class RetroMoviesClub extends Unit3dHandler {

    /**
     * {@inheritDoc}
     *
     * <p>
     * For {@link RetroMoviesClub}, the default transparent scrollbar still leaves a white bar on the right. Hiding the scrollbar entirely via
     * {@code display: none} removes the bar.
     */
    @Override
    public void actionBeforeScreenshot() {
        driver().executeScript("""
            var style = document.createElement('style');
            style.textContent = '::-webkit-scrollbar { display: none !important; width: 0 !important; }';
            document.head.appendChild(style);
            """);
    }
}
