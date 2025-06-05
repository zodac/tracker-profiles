#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     generate_changelog.sh
#
# Description:     Generates a categorized changelog from git commit messages
#                  between a previous tag and the current HEAD. Outputs the
#                  formatted changelog to the GitHub Actions environment.
#
# Usage:           ./generate_changelog.sh <previous_git_tag>
#
# Requirements:
#   - git must be installed and in the system PATH
#   - Executed within a git repository with a valid tag history
#   - Environment must define GITHUB_ENV (typically provided in GitHub Actions)
#
# Behavior:
#   - Reads all commits between the given tag and HEAD
#   - Parses commit messages expecting a format like: [category] message
#   - Categorizes commits by their bracketed prefix (e.g., [ci], [framework])
#   - Preferred categories (ci, deployment, doc, framework, python) are printed first
#   - Other categories are grouped under the "Trackers" section
#   - Commit messages are linked using the provided repository URL
#   - Outputs the changelog in a format suitable for use in GitHub Actions via $GITHUB_ENV
#
# Exit Codes:
#   - 0: Success
#   - Non-zero: Any failure due to missing arguments, git errors, or parsing issues
# ------------------------------------------------------------------------------

set -euo pipefail

PREV_TAG="${1}"
GIT_REPO_URL="https://github.com/zodac/tracker-profiles"

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
