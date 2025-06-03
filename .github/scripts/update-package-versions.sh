#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     get-latest-versions.sh
#
# Description:     Retrieves the latest available versions of a fixed set of
#                  Debian packages from the local APT cache and updates a
#                  Dockerfile's `apt-get install` block to pin these versions.
#
# Usage:           ./get-latest-versions.sh /path/to/Dockerfile
#
# Requirements:
#   - Host must run Debian or a derivative with `apt-cache` available
#   - Dockerfile must contain a marked install block between:
#       # BEGIN PINNED PACKAGE INSTALL
#       # END PINNED PACKAGE INSTALL
#
# Exit Codes:
#   - 0: Success
#   - 1: Invalid usage or version lookup failure
# ------------------------------------------------------------------------------
# TODO: Expand this to also update docker images/python packages
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo -e "Usage ::= $(basename "${0}") <path_to_Dockerfile>"
    exit 1
fi

dockerfile="${1}"

if [[ ! -f "${dockerfile}" ]]; then
    echo "❌ File not found: ${dockerfile}"
    exit 1
fi

START_MARKER="# BEGIN PINNED PACKAGE INSTALL"
END_MARKER="# END PINNED PACKAGE INSTALL"

if ! grep -q "${START_MARKER}" "${dockerfile}" || ! grep -q "${END_MARKER}" "${dockerfile}"; then
    echo "❌ Could not find marker lines in ${dockerfile}"
    exit 1
fi

WANTED_PACKAGES=(
  chromium
  curl
  fonts-arphic-ukai
  gosu
  x11-apps
)

declare -A versions

echo "🔍 Fetching latest package versions..."
for package in "${WANTED_PACKAGES[@]}"; do
  version=$(apt-cache policy "${package}" | awk '/Candidate:/ { print $2 }')
  if [[ -z "$version" ]]; then
    echo "❌ Failed to get version for: ${package}"
    exit 1
  fi
  versions["${package}"]="${version}"
  echo "  ${package}=${version}"
done

# Build the replacement block
REPLACEMENT=$(cat <<EOF
${START_MARKER}
RUN apt-get update && \\
    apt-get install -yqq --no-install-recommends \\
      chromium="${versions[chromium]}" \\
      curl="${versions[curl]}" \\
      fonts-arphic-ukai="${versions[fonts-arphic-ukai]}" \\
      gosu="${versions[gosu]}" \\
      x11-apps="${versions[x11-apps]}" \\
    && \\
    apt-get autoremove && \\
    apt-get clean && \\
    rm -rf /var/lib/apt/lists/*
${END_MARKER}
EOF
)

# Replace the block between markers using awk
awk -v block="${REPLACEMENT}" '
BEGIN { in_block = 0 }
/# BEGIN PINNED PACKAGE INSTALL/ { print block; in_block = 1; next }
/# END PINNED PACKAGE INSTALL/ { in_block = 0; next }
!in_block { print }
' "${dockerfile}" > "${dockerfile}.tmp"

mv "${dockerfile}.tmp" "${dockerfile}"
echo "✅ Dockerfile ${dockerfile} updated successfully"
