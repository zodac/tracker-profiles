#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     lint_and_tests.sh
#
# Description:     Lints and tests the project using Docker.
#
# Usage:           ./lint_and_tests.sh [steps]
#
#                  [steps] is an optional comma-separated list of steps to run.
#                  If omitted, only steps whose relevant files have changed since
#                  the most recent semver git tag are run. If no tag exists, all
#                  steps are run. Pass explicit steps to override auto-detection.
#
#                  Valid steps:
#                    docker      - Lint the Dockerfile with hadolint
#                    java        - Run Java lints and tests with Maven
#                    javascript  - Lint JavaScript files with eslint
#                    markdown    - Lint Markdown files with markdownlint-cli2
#                    python      - Lint Python files with ruff
#
#                  Examples:
#                    ./lint_and_tests.sh
#                    ./lint_and_tests.sh docker
#                    ./lint_and_tests.sh docker,javascript
#
# Requirements:
#   - Docker must be installed and available on the system PATH
#
# Exit Codes:
#   - 0: All linting and tests passed successfully
#   - Non-zero: One or more linting errors or test failures occurred
# ------------------------------------------------------------------------------

set -uo pipefail

trap 'echo; echo "❌ Interrupted"; exit 130' INT

ESLINT_BUILD_IMAGE="local/tracker-profiles-eslint:latest"
ESLINT_NODE_IMAGE="node:26.2.0-alpine"
HADOLINT_DOCKER_IMAGE="hadolint/hadolint:v2.14.0-alpine"
JAVA_BUILD_IMAGE="local/tracker-profiles-builder:latest"
JDK_DOCKER_IMAGE="eclipse-temurin:26_35-jdk"
MARKDOWNLINT_DOCKER_IMAGE="davidanson/markdownlint-cli2:v0.22.1"
MAVEN_DOCKER_IMAGE="maven:3.9.16"
RUFF_DOCKER_IMAGE="ghcr.io/astral-sh/ruff:0.15.14"

VALID_STEPS=("docker" "java" "javascript" "markdown" "python")

overall_exit_code=0

run_docker() {
    echo
    echo "Running Dockerfile lint using [${HADOLINT_DOCKER_IMAGE}]"
    docker pull "${HADOLINT_DOCKER_IMAGE}" >/dev/null
    if output=$(docker run --rm \
        -v "${PWD}":/app \
        -w /app \
        "${HADOLINT_DOCKER_IMAGE}" \
        hadolint --config ci/docker/.hadolint.yaml docker/Dockerfile 2>&1); then
        echo "✅ Dockerfile lint passed"
    else
        echo "${output}"
        echo "❌ Dockerfile lint failed"
        overall_exit_code=1
    fi
}

run_javascript() {
    echo
    echo "Running JavaScript lint using [${ESLINT_NODE_IMAGE}]"
    docker pull "${ESLINT_NODE_IMAGE}" >/dev/null
    build_output=$(
        docker build -t "${ESLINT_BUILD_IMAGE}" - 2>&1 <<EOF
FROM ${ESLINT_NODE_IMAGE}
RUN npm install -g eslint@9
EOF
    )
    # shellcheck disable=SC2181
    if [[ $? -ne 0 ]]; then
        echo "❌ JavaScript image build failed"
        echo "${build_output}"
        overall_exit_code=1
        return
    fi
    if output=$(docker run --rm \
        -v "${PWD}":/app \
        -w /app \
        "${ESLINT_BUILD_IMAGE}" \
        eslint --config ci/javascript/eslint.config.cjs \
        "tracker-profiles-screenshots/src/main/resources/net/zodac/tracker/redaction/*.js" 2>&1); then
        echo "✅ JavaScript lint passed"
    else
        echo "${output}"
        echo "❌ JavaScript lint failed"
        overall_exit_code=1
    fi
}

run_markdown() {
    echo
    echo "Running Markdown lint using [${MARKDOWNLINT_DOCKER_IMAGE}]"
    docker pull "${MARKDOWNLINT_DOCKER_IMAGE}" >/dev/null
    if output=$(docker run --rm \
        -v "${PWD}":/app \
        -w /app \
        "${MARKDOWNLINT_DOCKER_IMAGE}" \
        --config ci/doc/.markdownlint.json \
        "**/*.md" "!RELEASE_NOTES.md" "!tracker-profiles-screenshots/target/**" 2>&1); then
        echo "✅ Markdown lint passed"
    else
        echo "${output}"
        echo "❌ Markdown lint failed"
        overall_exit_code=1
    fi
}

run_java() {
    echo
    echo "Running Java lints and tests using [${MAVEN_DOCKER_IMAGE}] + [${JDK_DOCKER_IMAGE}]"
    docker pull "${MAVEN_DOCKER_IMAGE}" >/dev/null
    docker pull "${JDK_DOCKER_IMAGE}" >/dev/null
    build_output=$(
        docker build -t "${JAVA_BUILD_IMAGE}" - 2>&1 <<EOF
FROM ${MAVEN_DOCKER_IMAGE} AS maven_base
FROM ${JDK_DOCKER_IMAGE}
COPY --from=maven_base /usr/share/maven /usr/share/maven
ENV M2_HOME="/usr/share/maven"
ENV MAVEN_HOME="/usr/share/maven"
ENV PATH="\${M2_HOME}/bin:\${PATH}"
EOF
    )
    # shellcheck disable=SC2181
    if [[ $? -ne 0 ]]; then
        echo "❌ Java image build failed"
        echo "${build_output}"
        overall_exit_code=1
        return
    fi
    if output=$(docker run --rm \
        -u "$(id -u):$(id -g)" \
        -v "${PWD}":/app \
        -v "${HOME}/.m2":/var/maven/.m2 \
        -w /app \
        --entrypoint mvn \
        "${JAVA_BUILD_IMAGE}" \
        -Duser.home=/var/maven clean verify -Dall 2>&1); then
        echo "✅ Java lints and tests passed"
    else
        echo "${output}"
        echo "❌ Java lints and tests failed"
        overall_exit_code=1
    fi
}

run_python() {
    echo
    echo "Running Python lint using [${RUFF_DOCKER_IMAGE}]"
    docker pull "${RUFF_DOCKER_IMAGE}" >/dev/null
    if output=$(docker run --rm \
        -v "${PWD}":/app \
        -w /app \
        "${RUFF_DOCKER_IMAGE}" \
        check docker/scripts --config ci/python/ruff.toml 2>&1); then
        echo "✅ Python lint passed"
    else
        echo "${output}"
        echo "❌ Python lint failed"
        overall_exit_code=1
    fi
}

detect_changed_steps() {
    local latest_tag
    latest_tag=$(git tag --sort=-version:refname 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | head -1 || true)

    if [[ -z "${latest_tag}" ]]; then
        echo "No semver tag found; running all steps" >&2
        printf '%s\n' "${VALID_STEPS[@]}"
        return
    fi

    echo "Checking changes since tag [${latest_tag}]..." >&2

    local run_docker=false run_java=false run_javascript=false run_markdown=false run_python=false
    local java_changed_files=()
    local file

    while IFS= read -r file; do
        [[ -z "${file}" ]] && continue
        [[ "${file}" =~ ^docker/ || "${file}" =~ ^ci/docker/ ]] && run_docker=true
        if [[ "${file}" =~ ^tracker-profiles-screenshots/ || "${file}" =~ ^ci/java/ || "${file}" == "pom.xml" ]]; then
            run_java=true
            java_changed_files+=("${file}")
        fi
        [[ "${file}" =~ ^tracker-profiles-screenshots/src/main/resources/net/zodac/tracker/redaction/ || "${file}" =~ ^ci/javascript/ ]] && run_javascript=true
        [[ "${file}" == "README.md" || "${file}" =~ ^ci/doc/ ]] && run_markdown=true
        [[ "${file}" =~ ^docker/scripts/.*\.py$ || "${file}" =~ ^ci/python/ ]] && run_python=true
    done < <(
        {
            git diff --name-only "${latest_tag}..HEAD"
            git diff --name-only
            git diff --name-only --cached
            git ls-files --others --exclude-standard
        } | sort -u
    )

    # Suppress the java step if the only change is a <version> bump in the two pom.xml files
    if [[ "${run_java}" == true ]]; then
        local only_poms=true
        local changed_file
        for changed_file in "${java_changed_files[@]}"; do
            if [[ "${changed_file}" != "pom.xml" && "${changed_file}" != "tracker-profiles-screenshots/pom.xml" ]]; then
                only_poms=false
                break
            fi
        done
        if [[ "${only_poms}" == true ]]; then
            local non_version_diff
            non_version_diff=$(
                {
                    git diff "${latest_tag}..HEAD" -- pom.xml "tracker-profiles-screenshots/pom.xml" 2>/dev/null
                    git diff -- pom.xml "tracker-profiles-screenshots/pom.xml" 2>/dev/null
                    git diff --cached -- pom.xml "tracker-profiles-screenshots/pom.xml" 2>/dev/null
                } | grep '^[+-]' | grep -v '^---\|^+++' | grep -vE '^[+-][[:space:]]*<version>'
            )
            if [[ -z "${non_version_diff}" ]]; then
                run_java=false
                echo "Skipping java: only pom.xml version bump detected" >&2
            fi
        fi
    fi

    # A bump to any docker image pinned in this script must re-trigger the matching step,
    # since the image change won't otherwise show up in the file-path detection above.
    # Runs after the pom-version-bump suppress so an image change can re-enable a suppressed step.
    local script_path=".github/scripts/lint_and_tests.sh"
    local script_diff
    script_diff=$(
        {
            git diff "${latest_tag}..HEAD" -- "${script_path}" 2>/dev/null
            git diff -- "${script_path}" 2>/dev/null
            git diff --cached -- "${script_path}" 2>/dev/null
        } | grep -E '^[+-][^+-]' || true
    )
    if [[ -n "${script_diff}" ]]; then
        grep -qE '^[+-][[:space:]]*HADOLINT_DOCKER_IMAGE='                                <<<"${script_diff}" && run_docker=true
        grep -qE '^[+-][[:space:]]*(ESLINT_BUILD_IMAGE|ESLINT_NODE_IMAGE)='               <<<"${script_diff}" && run_javascript=true
        grep -qE '^[+-][[:space:]]*(JAVA_BUILD_IMAGE|JDK_DOCKER_IMAGE|MAVEN_DOCKER_IMAGE)=' <<<"${script_diff}" && run_java=true
        grep -qE '^[+-][[:space:]]*MARKDOWNLINT_DOCKER_IMAGE='                            <<<"${script_diff}" && run_markdown=true
        grep -qE '^[+-][[:space:]]*RUFF_DOCKER_IMAGE='                                    <<<"${script_diff}" && run_python=true
    fi

    [[ "${run_docker}"     == true ]] && echo "docker"
    [[ "${run_java}"       == true ]] && echo "java"
    [[ "${run_javascript}" == true ]] && echo "javascript"
    [[ "${run_markdown}"   == true ]] && echo "markdown"
    [[ "${run_python}"     == true ]] && echo "python"
}

# Parse and validate steps
if [[ $# -eq 0 ]]; then
    mapfile -t steps < <(detect_changed_steps)
    if [[ ${#steps[@]} -eq 0 ]]; then
        echo "No relevant changes detected since last tag; nothing to run"
        exit 0
    fi
    echo "Running steps: $(IFS=', '; echo "${steps[*]}")"
else
    IFS=',' read -ra steps <<<"${1}"
    for step in "${steps[@]}"; do
        pattern=" ${step} "
        if [[ ! " ${VALID_STEPS[*]} " =~ $pattern ]]; then
            echo "❌ Unknown step: '${step}'. Valid steps: $(
                IFS=', '
                echo "${VALID_STEPS[*]}"
            )"
            exit 1
        fi
    done
fi

# Execute steps
for step in "${steps[@]}"; do
    case "${step}" in
    docker) run_docker ;;
    java) run_java ;;
    javascript) run_javascript ;;
    markdown) run_markdown ;;
    python) run_python ;;
    esac
done

if [[ "${overall_exit_code}" -ne 0 ]]; then
    echo
    echo "❌ One or more steps failed"
    exit 1
fi
