#!/bin/bash

set -euo pipefail

PREV_TAG="${1}"
GIT_REPO_URL="${2}"

# Get commit messages with commit hash and raw message separated
COMMITS=$(git log "${PREV_TAG}"..HEAD --pretty=format:"%h%n%B%n---END---")

declare -A categories
commit_hash=""
commit_message=""

# Process commits and categorize them
while IFS= read -r line; do
  if [[ "$line" == "---END---" ]]; then
    # Process the full commit message line by line
    while IFS= read -r msg_line; do
      if [[ "$msg_line" =~ ^\[([A-Za-z0-9_.-]+)\]\ (.+) ]]; then
        category="${BASH_REMATCH[1]}"
        message="${BASH_REMATCH[2]}"
        categories["${category}"]+="- [[${commit_hash}](${GIT_REPO_URL}/commit/${commit_hash})] ${message}"$'\n'
      fi
    done <<< "$commit_message"

    # Reset for next commit
    commit_hash=""
    commit_message=""
  elif [[ -z "$commit_hash" ]]; then
    commit_hash="$line"
  else
    commit_message+="$line"$'\n'
  fi
done <<< "${COMMITS}"

# Generate changelog content
CHANGELOG_CONTENT=$(mktemp)

{
  preferred=("ci" "deployment" "doc" "framework" "python")
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
    if [[ -z "${printed[${cat}]+x}" ]]; then
      other_cats+=("${cat}")
    fi
  done

  if [ ${#other_cats[@]} -gt 0 ]; then
    echo "*Trackers:*"
    IFS=$'\n' && mapfile -t sorted < <(printf "%s\n" "${other_cats[@]}" | sort) && unset IFS
    for cat in "${sorted[@]}"; do
      echo "**[${cat}]**"
      echo "${categories[$cat]}"
      echo ""
    done
  fi
} >> "${CHANGELOG_CONTENT}"

# Output changelog content
{
  echo "changelog_content<<EOF"
  cat "${CHANGELOG_CONTENT}"
  echo "EOF"
} >> "${GITHUB_ENV}"
