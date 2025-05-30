#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     start.sh
#
# Description:     Launches a headless instance of Google Chrome and runs a Java
#                  application (`tracker-profiles.jar`) that performs screenshot
#                  capture via Chrome's remote debugging protocol.
#
# Usage:           ./start.sh
#
# Requirements:    - Google Chrome/Chromium installed and accessible as `google-chrome-stable`
#                  - Java installed and available on the system PATH
#                  - `tracker-profiles.jar` available at /app/tracker-profiles.jar
#                  - X display server running and accessible at DISPLAY=:0
#
# Behavior:
#   - Starts Chrome in headless remote debugging mode on port 9222
#   - Executes the Java JAR file
#   - Outputs a colored success or error message based on Java's exit code
#   - Tracks and prints total execution time in a natural format
#   - Ensures Chrome is terminated on SIGINT (Ctrl+C)
#
# Exit Codes:
#   - 0: Success
#   - 1: Java application signaled failure (e.g., screenshots not captured)
#   - 130: Script terminated via SIGINT (manual interruption)
# ------------------------------------------------------------------------------

set -euo pipefail

main() {
    start_time=$(date +%s%3N)

    # Start Google Chrome in the background with suppressed output
    google-chrome-stable --remote-debugging-port=9222 --display=:0 >/dev/null 2>&1 &
    CHROME_PID=$!

    # Run Java application
    java -jar /app/tracker-profiles.jar
    JAVA_EXIT_CODE=$?

    if [[ ${JAVA_EXIT_CODE} -eq 1 ]]; then
        echo -e "\e[31mFailed to take screenshots, please review logs\e[0m"
        kill $$
    else
        echo -e "\e[32mScreenshots complete in $(get_execution_time "${start_time}")\e[0m"
    fi
}

get_execution_time() {
    local start_time="${1}"
    end_time=$(date +%s%3N)
    elapsed_time=$((end_time - start_time))
    _convert_to_natural_time "${elapsed_time}"
}

_convert_to_natural_time() {
    local elapsed_time="${1}"
    local elapsed_s elapsed_m natural_time

    if [ "${elapsed_time}" -lt 1000 ]; then # Less than 1 second
        natural_time="${elapsed_time}ms"
    elif [ "${elapsed_time}" -lt 60000 ]; then # Less than 1 minute
        elapsed_s=$((elapsed_time / 1000))
        natural_time="${elapsed_s}s"
    elif [ "${elapsed_time}" -lt 3600000 ]; then # Less than 1 hour
        elapsed_m=$((elapsed_time / 60000))
        elapsed_s=$(((elapsed_time % 60000) / 1000))
        natural_time="${elapsed_m}m:${elapsed_s}s"
    else # More than an hour
        elapsed_h=$((elapsed_time / 3600000))
        elapsed_m=$(((elapsed_time % 3600000) / 60000))
        elapsed_s=$(((elapsed_time % 60000) / 1000))
        natural_time="${elapsed_h}h:${elapsed_m}m:${elapsed_s}s"
    fi

    echo "${natural_time}"
}

# Function to handle termination signals
cleanup() {
    kill -SIGTERM "${CHROME_PID}" 2>/dev/null
    wait "${CHROME_PID}"
    exit 130
}

trap cleanup SIGINT
main