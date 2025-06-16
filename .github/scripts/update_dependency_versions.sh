#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     update_dependency_versions.sh
#
# Description:     Updates version-pinned package declarations in both a Dockerfile
#                  and a Python requirements.txt file. Fetches the latest versions
#                  of Python, Debian, and Python pip packages, then rewrites the
#                  Dockerfile and requirements files with those values.
#
# Usage:           ./update_dependency_versions.sh <path_to_Dockerfile> <path_to_requirements.txt> <path_to_requirements_dev.txt>
#
# Requirements:
#   - bash, awk, grep, jq, curl
#   - Dockerfile must contain specific marker comments for python and debian package installs:
#       # BEGIN PYTHON PACKAGES / # END PYTHON PACKAGES
#       # BEGIN PINNED DEBIAN PACKAGE INSTALL / # END PINNED DEBIAN PACKAGE INSTALL
#   - requirements.txt should contain lines in the format: `package>=version` or `package==version`
#   - Internet access to fetch latest versions from PyPI and apt
#
# Behavior:
#   - For the Dockerfile:
#       - Updates Debian packages (defined in the Dockerfile) with the latest candidate versions from apt
#       - Updates Python tools (defined in the Dockerfile) with the latest versions from PyPI
#       - Rewrites the install blocks in the Dockerfile between respective markers
#   - For requirements.txt/requirements-dev.txt:
#       - Updates packages defined with `>=` to their latest PyPI versions
#       - Leaves `==` pinned packages untouched
#       - Overwrites the original requirements.txt with the updated values
#
# Exit Codes:
#   - 0: Success
#   - 1: Failure due to missing markers or failed version fetches
# ------------------------------------------------------------------------------

set -euo pipefail

get_pypi_version() {
    curl -s "https://pypi.org/pypi/${1}/json" | jq -r '.info.version'
}

update_requirements() {
    for requirements in "$@"; do
        name=$(basename "${requirements}")

        echo
        echo "🔍 Fetching ${name} versions from PyPI..."
        while IFS= read -r line; do
            # Match 'package>=version'
            if [[ $line =~ ^([a-zA-Z0-9._-]+)'>='([0-9a-zA-Z._-]+)$ ]]; then
                package="${BASH_REMATCH[1]}"
                latest_version=$(get_pypi_version "${package}")
                echo "  ${package}=${latest_version}"
                echo "${package}>=${latest_version}" >>"${requirements}.tmp"

            # Match 'package==version'
            elif [[ $line =~ ^([a-zA-Z0-9._-]+)'=='([0-9a-zA-Z._-]+)$ ]]; then
                package="${BASH_REMATCH[1]}"
                pinned_version="${BASH_REMATCH[2]}"
                echo "  ${package}=${pinned_version} (pinned)"
                echo "${line}" >>"${requirements}.tmp"
            else
                echo "${line}" >>"${requirements}.tmp"
            fi
        done <"${requirements}"

        # Overwrite the original file
        mv "${requirements}.tmp" "${requirements}"
        echo "✅ ${requirements#./} updated successfully with latest packages"
    done
}

update_python_packages() {
    dockerfile="${1}"

    PYTHON_START_MARKER="# BEGIN PYTHON PACKAGES"
    PYTHON_END_MARKER="# END PYTHON PACKAGES"

    if ! grep -q "${PYTHON_START_MARKER}" "${dockerfile}" || ! grep -q "${PYTHON_END_MARKER}" "${dockerfile}"; then
        echo "❌ Could not find Python marker lines in ${dockerfile}"
        exit 1
    fi

    # Extract the lines between the start and end markers
    python_block=$(awk "/${PYTHON_START_MARKER}/,/^${PYTHON_END_MARKER}$/" "${dockerfile}")

    # Extract the package names before '=='
    mapfile -t package_names < <(echo "${python_block}" | grep -oP '^\s*[a-zA-Z0-9_.-]+(?===)' | sed 's/^[[:space:]]*//')

    if [[ "${#package_names[@]}" -eq 0 ]]; then
        echo "❌ No Python packages found in block"
        exit 1
    fi

    declare -A python_versions

    echo
    echo "🔍 Fetching latest Dockerfile Python versions from PyPI..."
    for package in "${package_names[@]}"; do
        version=$(get_pypi_version "${package}")
        if [[ -z "${version}" ]]; then
            echo "❌ Failed to get version for: ${package}"
            exit 1
        fi
        python_versions["${package}"]="${version}"
        echo "  ${package}=${version}"
    done

    # Build the updated install block
    PYTHON_REPLACEMENT="${PYTHON_START_MARKER}"$'\n'
    PYTHON_REPLACEMENT+="RUN python3 -m ensurepip && \\"$'\n'
    PYTHON_REPLACEMENT+="    pip install \\"$'\n'
    for package in "${package_names[@]}"; do
        PYTHON_REPLACEMENT+="        ${package}==\"${python_versions[${package}]}\" \\"$'\n'
    done
    # Remove trailing backslash
    PYTHON_REPLACEMENT="${PYTHON_REPLACEMENT%\\$'\n'}"$'\n'
    PYTHON_REPLACEMENT+="${PYTHON_END_MARKER}"

    # Replace the old block with the new one
    awk -v block="${PYTHON_REPLACEMENT}" \
        -v start_marker="${PYTHON_START_MARKER}" \
        -v end_marker="${PYTHON_END_MARKER}" '
    BEGIN { in_block = 0 }
    $0 ~ start_marker { print block; in_block = 1; next }
    $0 ~ end_marker { in_block = 0; next }
    !in_block { print }
    ' "${dockerfile}" >"${dockerfile}.tmp"

    mv "${dockerfile}.tmp" "${dockerfile}"
    echo "✅ ${dockerfile#./} updated successfully with latest Python packages"
}

update_debian_packages() {
    dockerfile="${1}"

    DEBIAN_START_MARKER="# BEGIN DEBIAN PACKAGES"
    DEBIAN_END_MARKER="# END DEBIAN PACKAGES"

    if ! grep -q "${DEBIAN_START_MARKER}" "${dockerfile}" || ! grep -q "${DEBIAN_END_MARKER}" "${dockerfile}"; then
        echo "❌ Could not find Debian marker lines in ${dockerfile}"
        exit 1
    fi

    get_debian_version() {
        apt-cache policy "${1}" | awk '/Candidate:/ { print $2 }'
    }

    # Extract the lines between the start and end markers
    package_block=$(awk "/${DEBIAN_START_MARKER}/,/^${DEBIAN_END_MARKER}$/" "${dockerfile}")

    # Extract the package names before '=' using regex
    mapfile -t package_names < <(echo "${package_block}" | grep -oP '^\s*[a-z0-9.+-]+(?==)' | sed 's/^[[:space:]]*//')

    if [[ "${#package_names[@]}" -eq 0 ]]; then
        echo "❌ No package names found in block"
        exit 1
    fi

    declare -A debian_versions

    echo
    echo "🔍 Fetching latest dockerfile Debian package versions..."
    for package in "${package_names[@]}"; do
        version=$(get_debian_version "${package}")
        if [[ -z "${version}" ]]; then
            echo "❌ Failed to get version for: ${package}"
            exit 1
        fi
        debian_versions["${package}"]="${version}"
        echo "  ${package}=${version}"
    done

    # Build the updated install block
    DEBIAN_REPLACEMENT="${DEBIAN_START_MARKER}"$'\n'
    DEBIAN_REPLACEMENT+="RUN apt-get update && \\"$'\n'
    DEBIAN_REPLACEMENT+="    apt-get install -yqq --no-install-recommends \\"$'\n'
    for package in "${package_names[@]}"; do
        DEBIAN_REPLACEMENT+="        ${package}=\"${debian_versions[${package}]}\" \\"$'\n'
    done
    DEBIAN_REPLACEMENT+="    && \\"$'\n'
    DEBIAN_REPLACEMENT+="    apt-get autoremove && \\"$'\n'
    DEBIAN_REPLACEMENT+="    apt-get clean && \\"$'\n'
    DEBIAN_REPLACEMENT+="    rm -rf /var/lib/apt/lists/*"$'\n'
    DEBIAN_REPLACEMENT+="${DEBIAN_END_MARKER}"

    # Replace the old block with the new one
    awk -v block="${DEBIAN_REPLACEMENT}" \
        -v start_marker="${DEBIAN_START_MARKER}" \
        -v end_marker="${DEBIAN_END_MARKER}" '
    BEGIN { in_block = 0 }
    $0 ~ start_marker { print block; in_block = 1; next }
    $0 ~ end_marker { in_block = 0; next }
    !in_block { print }
    ' "${dockerfile}" >"${dockerfile}.tmp"

    mv "${dockerfile}.tmp" "${dockerfile}"
    echo "✅ ${dockerfile#./} updated successfully with latest Debian packages"
}

# Default paths assume the script is being run from the root of the project
dockerfile="${1:-./docker/Dockerfile}"
requirements="${2:-./python/requirements.txt}"
requirements_dev="${3:-./python/requirements-dev.txt}"

if [[ ! -f "${dockerfile}" ]]; then
    echo "❌ Dockerfile not found: ${dockerfile}"
    exit 1
fi
if [[ ! -f "${requirements}" ]]; then
    echo "❌ Python requirements.txt not found: ${requirements}"
    exit 1
fi
if [[ ! -f "${requirements_dev}" ]]; then
    echo "❌ Python requirements-dev.txt not found: ${requirements_dev}"
    exit 1
fi

update_debian_packages "${dockerfile}"
update_python_packages "${dockerfile}"
update_requirements "${requirements}" "${requirements_dev}"
