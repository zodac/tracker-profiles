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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.zodac.tracker.framework.ExecutionCancellation;
import net.zodac.tracker.framework.TrackerType;
import net.zodac.tracker.framework.config.Configuration;
import net.zodac.tracker.framework.driver.extension.Extension;
import net.zodac.tracker.framework.exception.ExecutionCancelledException;
import org.junit.jupiter.api.AfterEach;
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
    void resetPool() throws ReflectiveOperationException {
        final DriverPool instance = getInstance();
        clearField(instance, "idleDrivers");
        clearField(instance, "pooledDrivers");
        clearField(instance, "freshDrivers");
        setCapacity(Integer.MAX_VALUE);
        ExecutionCancellation.reset();
    }

    @AfterEach
    void resetCancellation() {
        ExecutionCancellation.reset();
    }

    @Test
    void givenCancelledExecution_whenAcquire_thenExceptionThrownAndNoDriverCreated() throws ReflectiveOperationException {
        injectIdleDriver(mock(RemoteWebDriver.class), TrackerType.HEADLESS);
        ExecutionCancellation.requestCancellation();

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            assertThatThrownBy(() -> DriverPool.acquire(TrackerType.HEADLESS, List.of()))
                .isInstanceOf(ExecutionCancelledException.class);
            assertThatThrownBy(() -> DriverPool.acquire(TrackerType.MANUAL, List.of()))
                .isInstanceOf(ExecutionCancelledException.class);
            factory.verifyNoInteractions();
        }
    }

    @Test
    void givenIdleDriverOfType_whenAcquire_thenIdleDriverReturned() throws ReflectiveOperationException {
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        injectIdleDriver(expected, TrackerType.HEADLESS);
        setCapacity(1);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            assertThat(DriverPool.acquire(TrackerType.HEADLESS, List.of()))
                .isSameAs(expected);
            factory.verifyNoInteractions();
        }
        assertThat(getIdleDrivers())
            .isEmpty();
    }

    @Test
    void givenIdleDriversOfBothTypes_whenAcquire_thenIdleDriverOfRequestedTypeReturned() throws ReflectiveOperationException {
        final RemoteWebDriver idleHeadless = mock(RemoteWebDriver.class);
        final RemoteWebDriver idleManual = mock(RemoteWebDriver.class);
        injectIdleDriver(idleHeadless, TrackerType.HEADLESS);
        injectIdleDriver(idleManual, TrackerType.MANUAL);

        assertThat(DriverPool.acquire(TrackerType.MANUAL, List.of()))
            .isSameAs(idleManual);
        assertThat(getIdleDrivers())
            .containsExactly(idleHeadless);
    }

    @Test
    void givenNoIdleDriverAndSpareCapacity_whenAcquire_thenPooledDriverCreatedAndIdleDriversKept() throws ReflectiveOperationException {
        final RemoteWebDriver idleHeadless = mock(RemoteWebDriver.class);
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        injectIdleDriver(idleHeadless, TrackerType.HEADLESS);
        setCapacity(2);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(TrackerType.MANUAL, List.of())).thenReturn(expected);

            assertThat(DriverPool.acquire(TrackerType.MANUAL, List.of()))
                .isSameAs(expected);
        }
        verify(idleHeadless, never()).quit();
        assertThat(getPooledDrivers())
            .containsEntry(expected, TrackerType.MANUAL)
            .containsKey(idleHeadless);
    }

    @Test
    void givenFullPoolWithIdleDriverOfOtherType_whenAcquire_thenIdleDriverQuitAndNewDriverCreated() throws ReflectiveOperationException {
        final RemoteWebDriver idleHeadless = mock(RemoteWebDriver.class);
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        injectIdleDriver(idleHeadless, TrackerType.HEADLESS);
        setCapacity(1);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(TrackerType.MANUAL, List.of())).thenReturn(expected);

            assertThat(DriverPool.acquire(TrackerType.MANUAL, List.of()))
                .isSameAs(expected);
        }
        verify(idleHeadless).quit();
        assertThat(getPooledDrivers())
            .containsOnlyKeys(expected);
    }

    @Test
    void givenExtensions_whenAcquire_thenFreshDriverCreatedAndConfigured() throws ReflectiveOperationException {
        final RemoteWebDriver expected = mock(RemoteWebDriver.class);
        final Extension extension = mock(Extension.class);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList())).thenReturn(expected);

            assertThat(DriverPool.acquire(TrackerType.HEADLESS, List.of(extension)))
                .isSameAs(expected);
        }
        verify(extension).configure(expected);
        assertThat(getFreshDrivers())
            .contains(expected);
        assertThat(getPooledDrivers())
            .isEmpty();
    }

    @Test
    void givenExtensionsAndFullPool_whenAcquire_thenIdleDriverQuitAndFreshDriverQuitOnRelease() throws ReflectiveOperationException {
        final RemoteWebDriver idle = mock(RemoteWebDriver.class);
        final RemoteWebDriver fresh = mock(RemoteWebDriver.class);
        final Extension extension = mock(Extension.class);
        injectIdleDriver(idle, TrackerType.HEADLESS);
        setCapacity(1);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList())).thenReturn(fresh);

            final RemoteWebDriver acquired = DriverPool.acquire(TrackerType.HEADLESS, List.of(extension));
            verify(idle).quit();
            assertThat(acquired)
                .isSameAs(fresh);

            DriverPool.release(acquired);
            factory.verify(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList()));
        }
        verify(fresh).quit();
        assertThat(getFreshDrivers())
            .isEmpty();
    }

    @Test
    void givenFreshExtensionDriver_whenShutdown_thenFreshDriverAlsoQuit() {
        final RemoteWebDriver fresh = mock(RemoteWebDriver.class);
        final Extension extension = mock(Extension.class);

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            factory.when(() -> JavaWebDriverFactory.createDriver(TrackerType.HEADLESS, List.of(extension))).thenReturn(fresh);
            DriverPool.acquire(TrackerType.HEADLESS, List.of(extension));
            DriverPool.shutdown();
        }

        verify(fresh).quit();
    }

    @Test
    void givenHealthyPooledDriver_whenRelease_thenDriverReturnedToIdleDrivers() throws ReflectiveOperationException {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class);
        final WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
        when(driver.navigate()).thenReturn(navigation);
        injectIdleDriver(driver, TrackerType.MANUAL);
        DriverPool.acquire(TrackerType.MANUAL, List.of());

        DriverPool.release(driver);
        assertThat(getIdleDrivers())
            .containsExactly(driver);
    }

    @Test
    void givenUnknownDriver_whenRelease_thenDriverQuit() {
        final RemoteWebDriver driver = mock(RemoteWebDriver.class);
        try (final var _ = mockStatic(JavaWebDriverFactory.class)) {
            DriverPool.release(driver);
        }

        verify(driver).quit();
    }

    @Test
    void givenDeadPooledDriver_whenRelease_thenDriverQuitAndNotReplaced() throws ReflectiveOperationException {
        final RemoteWebDriver deadDriver = mock(RemoteWebDriver.class);
        when(deadDriver.navigate()).thenThrow(new WebDriverException("session closed"));
        injectIdleDriver(deadDriver, TrackerType.HEADLESS);
        DriverPool.acquire(TrackerType.HEADLESS, List.of());

        try (final MockedStatic<JavaWebDriverFactory> factory = mockStatic(JavaWebDriverFactory.class)) {
            DriverPool.release(deadDriver);
            factory.verify(() -> JavaWebDriverFactory.createDriver(any(TrackerType.class), anyList()), never());
        }

        verify(deadDriver).quit();
        assertThat(getPooledDrivers())
            .isEmpty();
        assertThat(getIdleDrivers())
            .isEmpty();
    }

    @Test
    void givenPooledDrivers_whenShutdown_thenAllDriversQuitAndStateCleared() throws ReflectiveOperationException {
        final RemoteWebDriver driver1 = mock(RemoteWebDriver.class);
        final RemoteWebDriver driver2 = mock(RemoteWebDriver.class);
        injectIdleDriver(driver1, TrackerType.HEADLESS);
        injectIdleDriver(driver2, TrackerType.MANUAL);

        try (final var _ = mockStatic(JavaWebDriverFactory.class)) {
            DriverPool.shutdown();
        }

        verify(driver1).quit();
        verify(driver2).quit();
        assertThat(getPooledDrivers())
            .isEmpty();
        assertThat(getIdleDrivers())
            .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Deque<RemoteWebDriver> getIdleDrivers() throws ReflectiveOperationException {
        return (Deque<RemoteWebDriver>) getField(getInstance(), "idleDrivers");
    }

    @SuppressWarnings("unchecked")
    private static Map<RemoteWebDriver, TrackerType> getPooledDrivers() throws ReflectiveOperationException {
        return (Map<RemoteWebDriver, TrackerType>) getField(getInstance(), "pooledDrivers");
    }

    @SuppressWarnings("unchecked")
    private static Collection<RemoteWebDriver> getFreshDrivers() throws ReflectiveOperationException {
        return (Collection<RemoteWebDriver>) getField(getInstance(), "freshDrivers");
    }

    private static void injectIdleDriver(final RemoteWebDriver driver, final TrackerType type) throws ReflectiveOperationException {
        getPooledDrivers().put(driver, type);
        getIdleDrivers().addLast(driver);
    }

    private static void setCapacity(final int capacity) throws ReflectiveOperationException {
        final Field field = DriverPool.class.getDeclaredField("capacity");
        field.setAccessible(true); // NOPMD: AvoidAccessibilityAlteration - required to set private fields in tests
        field.setInt(getInstance(), capacity);
    }

    private static void clearField(final DriverPool instance, final String fieldName) throws ReflectiveOperationException {
        final Object value = getField(instance, fieldName);
        if (value instanceof final Map<?, ?> map) {
            map.clear();
        } else if (value instanceof final Collection<?> col) {
            col.clear();
        }
    }

    private static Object getField(final DriverPool instance, final String fieldName) throws ReflectiveOperationException {
        final Field field = DriverPool.class.getDeclaredField(fieldName);
        field.setAccessible(true); // NOPMD: AvoidAccessibilityAlteration - required to read private fields in tests
        return field.get(instance);
    }

    private static DriverPool getInstance() throws ReflectiveOperationException {
        final Class<?> holderClass = Class.forName("net.zodac.tracker.framework.driver.DriverPool$InstanceHolder");
        final Field instanceField = holderClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true); // NOPMD: AvoidAccessibilityAlteration - required to read private fields in tests
        return (DriverPool) instanceField.get(null);
    }
}
