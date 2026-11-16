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

package net.zodac.tracker.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.zodac.tracker.framework.TrackerType;

/**
 * Annotation used to mark an implementation of {@link net.zodac.tracker.handler.AbstractTrackerHandler} to be used to generate screenshots for
 * specific websites.
 */
@Repeatable(TrackerHandlers.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TrackerHandler {

    /**
     * The name of the tracker website, which should link to an implementation of {@link net.zodac.tracker.handler.AbstractTrackerHandler}.
     *
     * @return the name of the tracker
     * @see net.zodac.tracker.framework.TrackerHandlerFactory
     */
    String name();

    /**
     * Defines the type of the tracker, to allow for certain types not to be run.
     *
     * @return the {@link TrackerType}
     */
    TrackerType type() default TrackerType.HEADLESS;

    /**
     * The URLs of the tracker website. Multiple can be included for sites with backups, but will be executed in order until one successfully loads.
     * This URL should load the login page of the tracker.
     *
     * @return the URLs of the tracker
     */
    String[] url();
}
