#!/bin/bash

set -euo pipefail

current_version="${1}"

IFS='.' read -r major minor patch <<< "${current_version}"
new_patch=$((patch + 1))
next_version="${major}.${minor}.${new_patch}"

echo "Bumping version to ${next_version}"
echo "${next_version}" > VERSION

# Set Maven project version to next development SNAPSHOT version
mvn versions:set -DnewVersion="${next_version}-SNAPSHOT" -DgenerateBackupPoms=false -DprocessAllModules

# Push changes
git add -- VERSION pom.xml ./*/pom.xml

if git diff --cached --quiet; then
  echo "No changes to commit"
else
  git commit -m "[CI] Prepare next version: ${next_version}"
  echo "has_changes=true" >> "${GITHUB_ENV}"
fi