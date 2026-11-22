#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     generate_readme_screenshots.sh
#
# Description:     Takes the screenshots of the web UI shown in the README, as
#                  WebP files to upload as assets of a GitHub release (so they
#                  are not kept in the git repository). Starts the Docker image,
#                  restores a backup of demo data (see
#                  python/readme_demo_backup.py), and
#                  takes a screenshot of each tab in the light and dark themes.
#
# Usage:           ./generate_readme_screenshots.sh <docker-image> <output-directory>
#
# Requirements:
#   - `docker`, `curl` and `python3`
#   - `cwebp` (from the `webp` package)
#   - Chrome or Chromium (`google-chrome`, `chromium` or `chromium-browser`,
#     or the path set in the `CHROME` environment variable)
#
# Output:          <output-directory>/web-ui-<tab>-<theme>.webp, for the tabs
#                  'main', 'trackers' and 'settings' and the themes 'light' and
#                  'dark' (README.md links to these names)
# ------------------------------------------------------------------------------
set -euo pipefail

image="${1:?Usage: generate_readme_screenshots.sh <docker-image> <output-directory>}"
output_directory="${2:?Usage: generate_readme_screenshots.sh <docker-image> <output-directory>}"

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
container_name="tracker-profiles-readme-screenshots"
port="${WEB_UI_PORT:-8090}"
url="http://127.0.0.1:${port}"
window_size="1440,960"
tabs=("main" "trackers" "settings")

chrome="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser || true)}"
if [[ -z "${chrome}" ]]; then
    echo "Chrome or Chromium is required (or set CHROME to its path)" >&2
    exit 1
fi
command -v cwebp > /dev/null || {
    echo "cwebp is required (install the 'webp' package)" >&2
    exit 1
}

work_directory="$(mktemp -d)"
cleanup() {
    docker rm --force "${container_name}" > /dev/null 2>&1 || true
    rm -rf "${work_directory}"
}
trap cleanup EXIT

mkdir -p "${output_directory}"
docker rm --force "${container_name}" > /dev/null 2>&1 || true

echo "Starting ${image}"
docker run --detach --name "${container_name}" \
    --cap-drop ALL --security-opt no-new-privileges=true --read-only --init --user 1000:1000 \
    --tmpfs /tmp:size=256m,noexec,nosuid,nodev \
    --env SECRET_KEY="readme-screenshots-secret-key" \
    --publish "127.0.0.1:${port}:8080" \
    "${image}" > /dev/null

for _ in $(seq 90); do
    curl --silent --fail "${url}/api/settings" > /dev/null && break
    if [[ "$(docker inspect --format '{{.State.Running}}' "${container_name}")" != "true" ]]; then
        docker logs "${container_name}" | tail -30
        exit 1
    fi
    sleep 1
done
curl --silent --fail "${url}/api/settings" > /dev/null || {
    echo "The web UI did not start" >&2
    docker logs "${container_name}" | tail -30
    exit 1
}

echo "Restoring the demo data"
python3 "${repository_root}/python/readme_demo_backup.py" "${work_directory}/demo_backup.zip"
curl --silent --show-error --fail --output /dev/null \
    --header "Content-Type: application/zip" --data-binary "@${work_directory}/demo_backup.zip" "${url}/api/backup"

# The web UI follows the colour scheme of the browser (nothing is saved in its storage, as each screenshot uses a new profile)
for theme in light dark; do
    if [[ "${theme}" == "dark" ]]; then
        colour_scheme="--force-dark-mode"
    else
        colour_scheme="--blink-settings=preferredColorScheme=1"
    fi

    for tab in "${tabs[@]}"; do
        png="${work_directory}/web-ui-${tab}-${theme}.png"
        "${chrome}" --headless --no-sandbox --disable-gpu --hide-scrollbars --user-data-dir="${work_directory}/chrome-${theme}-${tab}" \
            "${colour_scheme}" --window-size="${window_size}" --virtual-time-budget=5000 --screenshot="${png}" "${url}/#${tab}" 2> /dev/null
        cwebp -quiet -q 90 "${png}" -o "${output_directory}/web-ui-${tab}-${theme}.webp"
        echo "Saved ${output_directory}/web-ui-${tab}-${theme}.webp"
    done
done
