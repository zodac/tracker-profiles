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

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.jspecify.annotations.Nullable;

/**
 * Utility for discovering classes within a specific package at runtime, whether the classes live on the filesystem (typical IDE/test classpath) or
 * inside a JAR (typical packaged-application classpath).
 */
public final class PackageScanner {

    private PackageScanner() {

    }

    /**
     * Finds all classes within {@code packageName} (non-recursive) that match the provided {@code filter}.
     *
     * @param packageName the fully-qualified package to scan
     * @param filter      the {@link Predicate} that each discovered {@link Class} must satisfy to be included
     * @return the matching classes, ordered by class name
     * @throws IllegalStateException if the package cannot be located or read
     */
    public static Set<Class<?>> scan(final String packageName, final Predicate<Class<?>> filter) {
        final String packagePath = packageName.replace('.', '/');

        try {
            final URL resource = Thread.currentThread().getContextClassLoader().getResource(packagePath);
            if (resource == null) {
                throw new IllegalStateException("Unable to retrieve classes from package '%s'".formatted(packageName));
            }

            // If not running from a JAR, assume classes are available on the filesystem
            return "jar".equals(resource.getProtocol())
                ? getFromJar(resource, packagePath, filter)
                : getFromFile(resource, packageName, filter);
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalStateException("Unable to retrieve classes from package '%s'".formatted(packageName), e);
        }
    }

    private static Set<Class<?>> getFromFile(final URL resource, final String packageName, final Predicate<Class<?>> filter)
        throws URISyntaxException {
        final File[] directoryFiles = new File(resource.toURI()).listFiles();
        if (directoryFiles == null || directoryFiles.length == 0) {
            throw new IllegalStateException("Unable to retrieve classes from resource '%s'".formatted(resource.toURI()));
        }

        return Arrays.stream(directoryFiles)
            .map(File::getName)
            .filter(fileName -> fileName.endsWith(".class"))
            .map(fileName -> getClass(fileName, packageName))
            .filter(filter)
            .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::toString))));
    }

    private static Set<Class<?>> getFromJar(final URL resource, final String packagePath, final Predicate<Class<?>> filter) throws IOException {
        final JarURLConnection connection = (JarURLConnection) resource.openConnection();
        connection.setUseCaches(false);

        try (final JarFile jarFile = connection.getJarFile()) {
            return jarFile.stream()
                .map(ZipEntry::getName)
                .filter(jarEntryName -> jarEntryName.startsWith(packagePath) && jarEntryName.endsWith(".class"))
                .map(jarEntryName -> getClass(jarEntryName.replace('/', '.'), null))
                .filter(filter)
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::toString))));
        }
    }

    private static Class<?> getClass(final String className, final @Nullable String packageName) {
        try {
            final String prefix = packageName == null ? "" : (packageName + ".");
            return Class.forName(prefix + className.substring(0, className.lastIndexOf('.')));
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Unable to retrieve class '%s' from package '%s'".formatted(className, packageName), e);
        }
    }
}
