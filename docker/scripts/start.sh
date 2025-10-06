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

set -eu

main() {
    start_time=$(date +%s)

    case "${TRACKER_EXECUTION_ORDER:-}" in
        *cloudflare-check*)
            /app/venv/bin/python -m selenium_manager.server &
            PYTHON_PID=$!

            i=1
            while [ "${i}" -le 5 ]; do
                if curl -fs http://localhost:5000/ping >/dev/null; then
                    break
                fi
                sleep 1

                if [ "${i}" -eq 5 ]; then
                    echo "Failed to start Python service" >&2
                    kill "$PYTHON_PID" 2>/dev/null || true
                    exit 1
                fi
                i=$((i + 1))
            done
            ;;
    esac

    # TODO: Remote port needed anymore?
    chromium --remote-debugging-port=9222 --display=:0 >/dev/null 2>&1 &
    CHROME_PID=$!

    java -jar /app/tracker-profiles.jar
    JAVA_EXIT_CODE=$?

    if [ "${JAVA_EXIT_CODE}" -eq 1 ]; then
        printf '\033[31mFailed to take screenshots, please review logs\033[0m\n'
        exit 1
    else
        printf '\033[32mScreenshots complete in %s\033[0m\n' "$(get_execution_time "$start_time")"
    fi
}

get_execution_time() {
    start_time="${1}"
    end_time=$(date +%s)
    elapsed_time=$((end_time - start_time))
    _convert_to_natural_time "${elapsed_time}"
}

_convert_to_natural_time() {
    elapsed_time="${1}"
    if [ "${elapsed_time}" -lt 60 ]; then
        echo "${elapsed_time}s"
    elif [ "${elapsed_time}" -lt 3600 ]; then
        elapsed_m=$((elapsed_time / 60))
        elapsed_s=$((elapsed_time % 60))
        echo "${elapsed_m}m:${elapsed_s}s"
    else
        elapsed_h=$((elapsed_time / 3600))
        elapsed_m=$(((elapsed_time % 3600) / 60))
        elapsed_s=$((elapsed_time % 60))
        echo "${elapsed_h}h:${elapsed_m}m:${elapsed_s}s"
    fi
}

cleanup() {
    printf '\n\033[33mCleaning up...\033[0m\n'
    kill -TERM "${CHROME_PID:-}" 2>/dev/null || true
    wait "${CHROME_PID:-}" 2>/dev/null || true

    if [ -n "${PYTHON_PID:-}" ]; then
        kill -TERM "${PYTHON_PID}" 2>/dev/null || true
        wait "${PYTHON_PID}" 2>/dev/null || true
    fi

    exit 130
}

trap cleanup INT
main