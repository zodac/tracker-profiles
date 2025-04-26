#!/bin/bash

set -euo pipefail

PREV_TAG="${1}"

# Get commit messages
COMMITS=$(git log "${PREV_TAG}"..HEAD --pretty=format:"%s")
declare -A categories

# Process commits and categorize them
while IFS= read -r line; do
  if [[ "$line" =~ \[([A-Za-z0-9_-]+)\]\ (.+) ]]; then
    category="${BASH_REMATCH[1]}"
    message="${BASH_REMATCH[2]}"
    categories["${category}"]+="- ${message}"$'\n'
  fi
done <<< "${COMMITS}"

# Generate changelog content
CHANGELOG_CONTENT=$(mktemp)

{
  preferred=("ci" "docs" "framework")
  declare -A printed

  # Print preferred categories
  for key in $(printf "%s\n" "${preferred[@]}" | sort); do
    for cat in "${!categories[@]}"; do
      norm_cat=$(echo "${cat}" | tr '[:upper:]' '[:lower:]')
      if [[ "${norm_cat}" == "${key}" ]]; then
        echo "**[${cat}]**"
        echo "${categories[$cat]}"
        echo ""
        printed["${cat}"]=1
      fi
    done
  done

  # Print other categories
  other_cats=()
  for cat in "${!categories[@]}"; do
    if [[ -z "${printed[${cat}]}" ]]; then
      other_cats+=("${cat}")
    fi
  done

  if [ ${#other_cats[@]} -gt 0 ]; then
    echo "**[Trackers]**"
    IFS=$'\n' && mapfile -t sorted < <(printf "%s\n" "${other_cats[@]}" | sort) && unset IFS
    for cat in "${sorted[@]}"; do
      echo "**[${cat}]**"
      echo "${categories[$cat]}"
      echo ""
    done
  fi
} >> "${CHANGELOG_CONTENT}"

# Output changelog content
echo "changelog_content=${CHANGELOG_CONTENT}" >> "${GITHUB_ENV}"
