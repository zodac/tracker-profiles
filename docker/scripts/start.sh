#!/bin/sh
# ------------------------------------------------------------------------------
# Script Name:     start.sh
#
# Description:     Starts a virtual X11 display for the browsers with a UI, and
#                  runs the Java application (`tracker-profiles.jar`), which
#                  serves the web UI and takes the screenshots.
#
# Usage:           ./start.sh
#
# Requirements:
#   - Xvfb installed
#   - Java installed and available on the system PATH
#   - `tracker-profiles.jar` available at /app/tracker-profiles.jar
#
# Environment Variables:
#   - JAVA_ADDITIONAL_OPTS: Additional JVM options appended after whichever options are in effect
#   - JAVA_OPTS:            Replaces all default JVM options (defaults are used if unset or empty)
#
# Behavior:
#   - Starts a virtual X11 display (Xvfb), used by the browsers with a UI (which are shown in the web UI, so the host needs no display), and sets
#     DISPLAY to it, replacing any DISPLAY set for the container
#   - Executes the Java JAR file
#   - On SIGINT (Ctrl+C) or SIGTERM (`docker stop`), gracefully terminates the Java and Xvfb processes (if started)
#
# Exit Codes:
#   - 0: Success
#   - 1: Java application signaled failure (e.g., screenshots not captured)
#   - 130: Script terminated via SIGINT or SIGTERM
# ------------------------------------------------------------------------------

set -eu

# Large enough for the default browser resolutions (a larger browser window is not limited to the screen, so it still works)
VIRTUAL_DISPLAY=":99"
VIRTUAL_SCREEN="2560x1600x24"
VIRTUAL_DISPLAY_LOCK_FILE="/tmp/.X99-lock"

start_virtual_display() {
    # The X11 socket directory cannot be created as a non-root user, so Xvfb uses its abstract socket instead (and warns about it, so its output
    # is discarded). TCP is not needed, as the browsers run in this container
    Xvfb "${VIRTUAL_DISPLAY}" -screen 0 "${VIRTUAL_SCREEN}" -nolisten tcp >/dev/null 2>&1 &
    XVFB_PID=$!
    export DISPLAY="${VIRTUAL_DISPLAY}"

    # Xvfb creates its lock file once it is ready for connections
    attempts=0
    while [ ! -e "${VIRTUAL_DISPLAY_LOCK_FILE}" ] && [ "${attempts}" -lt 50 ]; do
        if ! kill -0 "${XVFB_PID}" 2>/dev/null; then
            printf '\033[31mVirtual display failed to start, browsers with a UI will not be available\033[0m\n'
            return
        fi
        attempts=$((attempts + 1))
        sleep 0.1
    done
}

main() {
    start_virtual_display

    DEFAULT_JAVA_OPTS="-Xms128m -Xmx512m \
      -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders \
      -XX:+UseG1GC -XX:ParallelGCThreads=4 -XX:ConcGCThreads=2 -XX:MaxGCPauseMillis=200 \
      -XX:SharedArchiveFile=/app/app.jsa -Xshare:auto \
      -Djava.util.logging.config.file=/app/logging.properties"

    # The SQLite native library is loaded from /app/native (extracted when the image is built), whichever JVM options are in effect, since
    # sqlite-jdbc would otherwise extract it to /tmp and load it from there, which fails when /tmp is mounted noexec
    # SC2086: intentional word splitting to pass JVM flags as separate arguments
    # shellcheck disable=SC2086
    java ${JAVA_OPTS:-${DEFAULT_JAVA_OPTS}} ${JAVA_ADDITIONAL_OPTS:-} \
      -Dorg.sqlite.lib.path=/app/native -Dorg.sqlite.lib.name=libsqlitejdbc.so \
      -jar /app/tracker-profiles.jar &
    JAVA_PID=$!

    wait "${JAVA_PID}"
}

cleanup() {
    printf '\n\033[33mCleaning up...\033[0m\n'

    # Stop Java process
    if [ -n "${JAVA_PID:-}" ]; then
        kill -TERM "${JAVA_PID}" 2>/dev/null || true
        wait "${JAVA_PID}" 2>/dev/null || true
    fi

    # Stop virtual display
    if [ -n "${XVFB_PID:-}" ]; then
        kill -TERM "${XVFB_PID}" 2>/dev/null || true
        wait "${XVFB_PID}" 2>/dev/null || true
    fi

    exit 130
}

trap cleanup INT TERM
main
