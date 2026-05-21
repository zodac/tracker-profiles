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

package net.zodac.tracker.framework.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.extension.Extension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Unit tests for {@link DriverPool}.
 */
class DriverPoolTest {

    @BeforeAll
    static void initConfig() {
        Configuration.init();
    }

    @BeforeEach
    void resetPool() throws Exception {
        final DriverPool instance = getInstance();
        clearField(instance, "pool");
        clearField(instance, "allPooledDrivers");
        clearField(instance, "driverTypeMap");
        setRemainingTasks(instance, 0);
    }

    @Test
    void givenPooledDriver_whenAcquire_thenDriverReturnedFromPool() throws Exception {
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        injectPooledDriver(expected, TrackerType.HEADLESS);

        final RemoteWebDriver actual = DriverPool.acquire(TrackerType.HEADLESS, List.of());
        assertThat(actual)
            .isSameAs(expected);
    }

    @Test
    void givenExtensions_whenAcquire_thenFreshDriverCreatedAndConfigured() {
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        final Extension extension = mock(Extension.class);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList())).thenReturn(expected);

            final RemoteWebDriver actual = DriverPool.acquire(TrackerType.HEADLESS, List.of(extension));
            verify(extension).configure(expected);
            assertThat(actual)
                .isSameAs(expected);
        }
    }

    @Test
    void givenNoPool_whenAcquire_thenLazyDriverCreatedAndRegistered() throws Exception {
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList())).thenReturn(expected);

            final RemoteWebDriver actual = DriverPool.acquire(TrackerType.HEADLESS, List.of());
            assertThat(actual)
                .isSameAs(expected);
            assertThat(getAllPooledDrivers())
                .contains(expected);
            assertThat(getDriverTypeMap())
                .containsKey(expected);
        }
    }

    @Test
    void givenHealthyPooledDriver_whenRelease_thenDriverReturnedToPool() throws Exception {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class);
        final WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
        when(driver.navigate()).thenReturn(navigation);
        injectPooledDriver(driver, TrackerType.HEADLESS);
        DriverPool.acquire(TrackerType.HEADLESS, List.of());

        DriverPool.release(driver);
        assertThat(getPool().get(TrackerType.HEADLESS))
            .contains(driver);
    }

    @Test
    void givenFreshDriver_whenRelease_thenDriverQuit() {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class);
        try (final var _ = mockStatic(JavaWebDriverFactory.class)) {
            DriverPool.release(driver);
        }

        verify(driver).quit();
    }

    @Test
    void givenDeadPooledDriver_whenRelease_thenDeadDriverReplacedInPool() throws Exception {
        final RemoteWebDriver deadDriver = mock(RemoteWebDriver.class);
        final RemoteWebDriver replacement = mock(RemoteWebDriver.class);
        when(deadDriver.navigate()).thenThrow(new WebDriverException("session closed"));
        injectPooledDriver(deadDriver, TrackerType.HEADLESS);
        DriverPool.acquire(TrackerType.HEADLESS, List.of());
        setRemainingTasks(getInstance(), 2);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList())).thenReturn(replacement);
            DriverPool.release(deadDriver);
        }

        assertThat(getAllPooledDrivers())
            .doesNotContain(deadDriver).contains(replacement);
        assertThat(getPool().get(TrackerType.HEADLESS))
            .contains(replacement);
    }

    @Test
    void givenPooledDrivers_whenShutdown_thenAllDriversQuitAndStateCleared() throws Exception {
        final RemoteWebDriver driver1 = mock(RemoteWebDriver.class);
        final RemoteWebDriver driver2 = mock(RemoteWebDriver.class);
        injectPooledDriver(driver1, TrackerType.HEADLESS);
        injectPooledDriver(driver2, TrackerType.MANUAL);

        try (final var _ = mockStatic(JavaWebDriverFactory.class)) {
            DriverPool.shutdown();
        }

        verify(driver1).quit();
        verify(driver2).quit();
        assertThat(getAllPooledDrivers())
            .isEmpty();
        assertThat(getPool())
            .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<TrackerType, BlockingDeque<RemoteWebDriver>> getPool() throws Exception {
        return (Map<TrackerType, BlockingDeque<RemoteWebDriver>>) getField(getInstance(), "pool");
    }

    @SuppressWarnings("unchecked")
    private static Set<RemoteWebDriver> getAllPooledDrivers() throws Exception {
        return (Set<RemoteWebDriver>) getField(getInstance(), "allPooledDrivers");
    }

    @SuppressWarnings("unchecked")
    private static Map<RemoteWebDriver, TrackerType> getDriverTypeMap() throws Exception {
        return (Map<RemoteWebDriver, TrackerType>) getField(getInstance(), "driverTypeMap");
    }

    private static void injectPooledDriver(final RemoteWebDriver driver, final TrackerType type) throws Exception {
        getAllPooledDrivers().add(driver);
        getDriverTypeMap().put(driver, type);
        getPool().computeIfAbsent(type, _ -> new LinkedBlockingDeque<>()).addLast(driver);
    }

    private static void clearField(final DriverPool instance, final String fieldName) throws Exception {
        final Object value = getField(instance, fieldName);
        if (value instanceof Map<?, ?> map) {
            map.clear();
        } else if (value instanceof Collection<?> col) {
            col.clear();
        }
    }

    private static void setRemainingTasks(final DriverPool instance, final int value) throws Exception {
        final Field field = DriverPool.class.getDeclaredField("remainingTasks");
        field.setAccessible(true);
        ((AtomicInteger) field.get(instance)).set(value);
    }

    private static Object getField(final DriverPool instance, final String fieldName) throws Exception {
        final Field field = DriverPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static DriverPool getInstance() throws Exception {
        final Class<?> holderClass = Class.forName("net.zodac.tracker.framework.driver.DriverPool$InstanceHolder");
        final Field instanceField = holderClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        return (DriverPool) instanceField.get(null);
    }
}
