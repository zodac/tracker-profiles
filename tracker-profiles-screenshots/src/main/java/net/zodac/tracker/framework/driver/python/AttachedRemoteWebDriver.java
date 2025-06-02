/*
 * BSD Zero Clause License
 *
 * Copyright (c) 2021-2025 zodac.net
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

package net.zodac.tracker.framework.driver.python;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import net.zodac.tracker.framework.exception.DriverAttachException;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.codec.w3c.W3CHttpCommandCodec;
import org.openqa.selenium.remote.codec.w3c.W3CHttpResponseCodec;

/**
 * Extension of {@link RemoteWebDriver} that can attach to an existing Selenium web browser session.
 */
public final class AttachedRemoteWebDriver extends RemoteWebDriver {

    private final SeleniumSession seleniumSession;

    private AttachedRemoteWebDriver(final SeleniumSession seleniumSession, final CommandExecutor executor) {
        super(executor, new DesiredCapabilities());
        this.seleniumSession = seleniumSession;
    }

    /**
     * Creates an instance of {@link AttachedRemoteWebDriver}. It retrieves the URL and port of an existing Python-based Selenium web browser session
     * from the provided {@link SeleniumSession}, then overrides the requires fields to attach to the existing session.
     *
     * @param seleniumSession the {@link SeleniumSession}
     * @return the created {@link AttachedRemoteWebDriver}.
     */
    public static AttachedRemoteWebDriver create(final SeleniumSession seleniumSession) {
        try {
            final HttpCommandExecutor executor = createCommandExecutor(seleniumSession);
            final AttachedRemoteWebDriver instance = new AttachedRemoteWebDriver(seleniumSession, executor);

            // Inject session ID into instance
            final Field sessionIdField = RemoteWebDriver.class.getDeclaredField("sessionId");
            setAccessible(sessionIdField);
            sessionIdField.set(instance, new SessionId(seleniumSession.sessionId()));

            return instance;
        } catch (final Exception e) {
            throw new DriverAttachException(e);
        }
    }

    private static HttpCommandExecutor createCommandExecutor(final SeleniumSession seleniumSession)
        throws MalformedURLException, NoSuchFieldException, IllegalAccessException {
        final URL remoteAddress = URI.create(seleniumSession.sessionUrl()).toURL();
        final HttpCommandExecutor executor = new HttpCommandExecutor(Map.of(), remoteAddress);

        // Inject command & response codecs
        final Field commandCodecField = HttpCommandExecutor.class.getDeclaredField("commandCodec");
        setAccessible(commandCodecField);
        commandCodecField.set(executor, new W3CHttpCommandCodec());

        final Field responseCodecField = HttpCommandExecutor.class.getDeclaredField("responseCodec");
        setAccessible(responseCodecField);
        responseCodecField.set(executor, new W3CHttpResponseCodec());
        return executor;
    }

    private static void setAccessible(final Field field) {
        field.setAccessible(true); // NOPMD: AvoidAccessibilityAlteration - Needed to override functionality of the driver
    }

    @Override
    protected void startSession(final Capabilities capabilities) {
        // Do nothing, in order to prevent new session creation
    }

    @Override
    public void quit() {
        super.quit();
        PythonHttpServerHandler.closeSession(seleniumSession);
    }
}
