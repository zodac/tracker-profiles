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
# Requirements:
#   - Google Chrome/Chromium installed and accessible as `google-chrome-stable`
#   - Java installed and available on the system PATH
#   - `tracker-profiles.jar` available at /app/tracker-profiles.jar
#   - X display server running and accessible at DISPLAY=:0
#   - Python 3.11+ installed and available on the system PATH and requirements.txt installed
#   - `selenium_manager.py` present in the working directory
#
# Behavior:
#   - If the environment variable TRACKER_EXECUTION_ORDER contains the string
#     "cloudflare-check":
#       - Starts `selenium_manager.py` in the background
#   - Starts Chrome in headless remote debugging mode on port 9222
#   - Executes the Java JAR file
#   - Outputs a colored success or error message based on Java's exit code
#   - Tracks and prints total execution time in a natural format
#   - On SIGINT (Ctrl+C), gracefully terminates Chrome and the Python process (if started)
#
# Exit Codes:
#   - 0: Success
#   - 1: Java application signaled failure (e.g., screenshots not captured)
#   - 130: Script terminated via SIGINT (manual interruption)
# ------------------------------------------------------------------------------

set -euo pipefail

main() {
    start_time=$(date +%s%3N)

    # If TRACKER_EXECUTION_ORDER contains "cloudflare-check", launch Python service
    if [[ "${TRACKER_EXECUTION_ORDER:-}" == *cloudflare-check* ]]; then
        # Run Python script in background
        /app/venv/bin/python /app/selenium_manager.py &
        PYTHON_PID=$!

        # Wait for /ping to return 200 OK or time out after 5s
        for i in {1..5}; do
            if curl -fs http://localhost:5000/ping >/dev/null; then
                break
            fi
            sleep 1

            # If this is the last iteration and still failing, exit
            if [[ $i -eq 20 ]]; then
                echo -e "\e[31mFailed to start Python service\e[0m"
                kill "${PYTHON_PID}" 2>/dev/null
                exit 1
            fi
        done
    fi

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
    echo -e "\n\e[33mCleaning up...\e[0m"
    kill -SIGTERM "${CHROME_PID}" 2>/dev/null || true
    wait "${CHROME_PID}" 2>/dev/null || true

    if [[ -n "${PYTHON_PID:-}" ]]; then
        kill -SIGTERM "${PYTHON_PID}" 2>/dev/null || true
        wait "${PYTHON_PID}" 2>/dev/null || true
    fi

    exit 130
}

trap cleanup SIGINT
main
