#!/bin/bash
# ------------------------------------------------------------------------------
# Script Name:     python_lint_and_tests.sh
#
# Description:     Lints and tests the Python Selenium manager code using Docker.
#                  Uses `ruff` for linting and `pytest` for running tests.
#
# Usage:           ./python_lint_and_tests.sh [ruff_arguments...]
#
# Requirements:
#   - Docker must be installed and available on the system PATH
#   - If run in GitHub Actions, the `CI=true` environment variable must be set
#
# Behavior:
#   - Runs `ruff` inside a container with optional arguments for linting the
#     `python/selenium_manager` directory using a custom config file
#   - Caches Ruff results in `.ruff_cache`
#   - If in CI, sets the container user to the host user ID for file permissions
#   - Runs `pytest` inside a container after installing dev requirements
#
# Exit Codes:
#   - 0: All linting and tests passed successfully
#   - Non-zero: One or more linting errors or test failures occurred
# ------------------------------------------------------------------------------

set -euo pipefail

LINT_DOCKER_IMAGE="ghcr.io/astral-sh/ruff:0.13.3"
TEST_DOCKER_IMAGE="python:3.13.7-alpine"

# Linting
echo
echo "🐳 Running lints using [${LINT_DOCKER_IMAGE}]"

docker run --rm \
    -v "${PWD}":/app \
    -w /app \
    "${LINT_DOCKER_IMAGE}" \
    check /app/python --config ci/python/ruff.toml --fix "$@"

# Tests
echo
echo "🐳 Running tests using [${TEST_DOCKER_IMAGE}]"

docker run --rm -t \
    -v "${PWD}":/app \
    -w /app \
    "${TEST_DOCKER_IMAGE}" \
    sh -c "
    export PYTHONPATH=/app/python &&
    pip install --quiet --upgrade pip --root-user-action=ignore &&
    pip install --quiet -r /app/python/requirements-dev.txt --root-user-action=ignore &&
    pytest -p no:cacheprovider -v /app/python/tests
  "
