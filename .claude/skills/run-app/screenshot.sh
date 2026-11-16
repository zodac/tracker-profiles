#!/usr/bin/env bash
# Builds the application, starts the web UI on a temporary database, and saves a full-page screenshot of each requested tab.
#
# Usage: screenshot.sh <output-directory> [tab...]   (tabs: main, trackers, settings; default: all three)
# Extra environment variables (such as NUMBER_OF_PARALLEL_THREADS=3) are passed through to the application.
set -euo pipefail

output_directory="${1:?Usage: screenshot.sh <output-directory> [tab...]}"
shift
if [[ $# -eq 0 ]]; then
    set -- main trackers settings
fi
tabs=("$@")

repository_root="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
port="${WEB_UI_PORT:-8099}"
run_directory="${output_directory}/run"
mkdir -p "${run_directory}/screenshots"
rm -f "${run_directory}/database.sqlite"*

if [[ "${SKIP_BUILD:-false}" != "true" ]]; then
    echo "Building the JAR"
    mvn -q -f "${repository_root}/pom.xml" clean install -DskipTests > "${run_directory}/build.log" 2>&1 || {
        tail -30 "${run_directory}/build.log"
        exit 1
    }
fi
jar="$(find "${repository_root}/tracker-profiles-screenshots/target" -maxdepth 1 -name 'tracker-profiles-screenshots-*.jar' ! -name 'original-*' | head -1)"

DATABASE_PATH="${run_directory}/database.sqlite" \
    SECRET_KEY="${SECRET_KEY:-screenshot-secret-key}" \
    WEB_UI_PORT="${port}" \
    OUTPUT_DIRECTORY_PARENT_PATH="${OUTPUT_DIRECTORY_PARENT_PATH:-${run_directory}/screenshots}" \
    java -jar "${jar}" > "${run_directory}/application.log" 2>&1 &
application_pid=$!
trap 'kill "${application_pid}" 2> /dev/null || true' EXIT

for _ in $(seq 60); do
    curl -sf "http://localhost:${port}/api/settings" > /dev/null && break
    if ! kill -0 "${application_pid}" 2> /dev/null; then
        tail -30 "${run_directory}/application.log"
        exit 1
    fi
    sleep 1
done

for tab in "${tabs[@]}"; do
    chromium --headless --no-sandbox --disable-gpu --hide-scrollbars --window-size="${WINDOW_SIZE:-1400,2200}" \
        --virtual-time-budget=4000 --screenshot="${output_directory}/${tab}.png" "http://localhost:${port}/#${tab}" 2> /dev/null
    echo "Saved ${output_directory}/${tab}.png"
done
