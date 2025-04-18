#!/bin/bash

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
    fi

    echo -e "\e[32mScreenshots complete in $(get_execution_time "${start_time}")\e[0m"
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