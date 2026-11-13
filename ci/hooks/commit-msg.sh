#!/bin/bash

# -----------------------------
# Static allowed categories
# -----------------------------
static_categories=(
  "AI"
  "CI"
  "Deployment"
  "Documentation"
  "Framework"
  "Removal"
  "UI"
)

# -----------------------------
# Dynamic categories from Java annotations
# -----------------------------

# @CommonTrackerHandler or @TrackerHandler
mapfile -t dynamic_categories < <(
  grep -R -h --include='*.java' \
    -E '^@(CommonTrackerHandler|TrackerHandler)' . \
    | cut -d '"' -f2 \
    | sort -u
)

# -----------------------------
# Merge and de-duplicate categories
# -----------------------------
mapfile -t allowed_categories < <(
  printf "%s\n" \
    "${static_categories[@]}" \
    "${dynamic_categories[@]}" \
  | sort -u
)

# -----------------------------
# Validation logic
# -----------------------------

regex="^\[([^]]+)\] .+"
commit_file="$1"
line_number=0
error_found=0

is_allowed_category() {
  local category="${1}"
  for allowed in "${allowed_categories[@]}"; do
    [[ "${category}" == "${allowed}" ]] && return 0
  done
  return 1
}

while IFS= read -r line || [[ -n "${line}" ]]; do
  line_number=$((line_number + 1))

  # Allow empty lines
  [[ -z "${line}" ]] && continue

  if [[ "${line}" =~ ${regex} ]]; then
    category="${BASH_REMATCH[1]}"

    if ! is_allowed_category "${category}"; then
      echo "Invalid commit category:"
      echo "L${line_number}: '${line}'"
      echo "→ '${category}' is not an allowed category"
      echo
      error_found=$((error_found + 1))
    fi
  else
    echo "Invalid commit message format:"
    echo "L${line_number}: '${line}'"
    echo
    error_found=$((error_found + 1))
  fi
done < "${commit_file}"

if [[ ${error_found} -gt 0 ]]; then
  echo "Each non-empty line must follow:"
  echo "  [Category] Commit message"
  echo

  echo "Allowed categories:"
  for cat in "${static_categories[@]}"; do
    echo "  - ${cat}"
  done

  # Auto-complete suggestions for invalid categories
  if [[ -n "${category}" ]]; then
    echo
    
    first_letter="${category:0:1}"   # First character of invalid category
    suggestions=()
    for cat in "${allowed_categories[@]}"; do
      if [[ "${cat}" == ${first_letter}* ]]; then
        suggestions+=("${cat}")
      fi
    done

    if [[ ${#suggestions[@]} -gt 0 ]]; then
      echo "Did you mean one of the following?"
      for s in "${suggestions[@]}"; do
        echo "  - ${s}"
      done
    fi
  fi

  exit 1
fi

exit 0
