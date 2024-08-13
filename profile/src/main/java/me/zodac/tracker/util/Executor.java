/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2024 zodac.me
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

package me.zodac.tracker.util;

import java.util.function.Consumer;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Utility class used to help execute actions using {@code Selenium}.
 */
public final class Executor {

    private Executor() {

    }

    /**
     * Executes a {@link Consumer} with a {@link ChromeDriver} in {@code headless} mode. Closes the {@link ChromeDriver} after the {@link Consumer}
     * is completed.
     *
     * @param consumer the {@link Consumer} to be executed, using the {@link ChromeDriver}
     */
    public static void executeWithDriver(final Consumer<? super ChromeDriver> consumer) {
        final ChromeDriver driver = create();

        try {
            consumer.accept(driver);
        } finally {
            driver.quit();
        }
    }

    private static ChromeDriver create() {
        final ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new");
        return new ChromeDriver(chromeOptions);
    }
}
