#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     get-latest-versions.sh
#
# Description:     Retrieves the latest available versions of a fixed set of
#                  Debian packages from the local APT cache and updates a
#                  Dockerfile's `apt-get install` command to pin these versions.
#
# Usage:           ./get-latest-versions.sh /path/to/Dockerfile
#
# Requirements:
#   - Host must be running Debian or a Debian-based distribution
#   - Dockerfile must contain a single `apt-get install` block that begins with
#     `apt-get update` and ends with `rm -rf /var/lib/apt/lists/*`
#   - The packages to be pinned must exist in the host’s APT sources
#
# Behavior:
#   - Parses the current candidate version of each predefined package using
#     `apt-cache policy`
#   - Rewrites the install block in the given Dockerfile using these versions
#   - Does not retain a backup of the original Dockerfile
#
# Exit Codes:
#   - 0: Success
#   - 1: Invalid usage, missing Dockerfile, or failure to retrieve version
# ------------------------------------------------------------------------------

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo -e "Usage ::= $(basename "${0}") <path_to_Dockerfile>"
    exit 1
fi

DOCKERFILE="${1}"

if [[ ! -f "${DOCKERFILE}" ]]; then
    echo "❌ File not found: ${DOCKERFILE}"
    exit 1
fi

# Define packages to check and update
PACKAGES=(
    chromium
    curl
    fonts-arphic-ukai
    gosu
    x11-apps
)

# Gather latest versions
declare -A VERSIONS

echo "🔍 Fetching latest available versions..."
for pkg in "${PACKAGES[@]}"; do
    version=$(apt-cache policy "${pkg}" | grep Candidate | awk '{print $2}')
    if [[ -z "${version}" ]]; then
        echo "❌ Could not retrieve version for: ${pkg}"
        exit 1
    fi

    VERSIONS["${pkg}"]="${version}"
    echo "📦 ${pkg}: ${version}"
done

# Create updated install line
UPDATED_INSTALL="RUN apt-get update && \\
    apt-get install -yqq --no-install-recommends \\"

for pkg in "${PACKAGES[@]}"; do
    UPDATED_INSTALL+="
    ${pkg}=\"${VERSIONS[${pkg}]}\" \\"
done

UPDATED_INSTALL+="
    && \\
    apt-get autoremove && \\
    apt-get clean && \\
    rm -rf /var/lib/apt/lists/*"

echo
echo "🛠 Updating Dockerfile: ${DOCKERFILE}"

# Use sed to replace the install block
sed -i -E "/apt-get update/,/rm -rf \/var\/lib\/apt\/lists\/\*/c\\
${UPDATED_INSTALL}
" "$DOCKERFILE"

echo "✅ Dockerfile updated successfully"
