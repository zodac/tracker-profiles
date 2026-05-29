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

package net.zodac.tracker.framework;

import net.zodac.tracker.framework.gui.DisplayUtils;
import net.zodac.tracker.util.BrowserInteractionHelper;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Runtime context bound to a single tracker execution, holding the {@link RemoteWebDriver} acquired for the run and the helpers built around it.
 *
 * <p>
 * Created by {@link net.zodac.tracker.handler.AbstractTrackerHandler#configure(TrackerDefinition)} immediately after instantiation and held as a
 * single set-once field, so the four components are either all present (handler is configured) or all absent (handler is not yet configured).
 *
 * @param driver                   the {@link RemoteWebDriver} acquired for this run
 * @param browserInteractionHelper the {@link BrowserInteractionHelper}
 * @param displayUtils             the {@link DisplayUtils}
 * @param trackerDefinition        the {@link TrackerDefinition} for the tracker being executed
 */
public record TrackerHandlerContext(
    RemoteWebDriver driver,
    BrowserInteractionHelper browserInteractionHelper,
    DisplayUtils displayUtils,
    TrackerDefinition trackerDefinition
) {

    /**
     * Static factory that builds the helpers around the supplied {@code driver}.
     *
     * @param trackerDefinition the {@link TrackerDefinition} for the tracker being executed
     * @param driver            the {@link RemoteWebDriver} acquired for this run
     * @return the {@link TrackerHandlerContext}
     */
    public static TrackerHandlerContext of(final TrackerDefinition trackerDefinition, final RemoteWebDriver driver) {
        return new TrackerHandlerContext(driver, BrowserInteractionHelper.of(driver), DisplayUtils.withDriver(driver), trackerDefinition);
    }
}
