#!/bin/bash
set -euo pipefail

LINT_DOCKER_IMAGE="ghcr.io/astral-sh/ruff:0.11.13"
TEST_DOCKER_IMAGE="python:3.13.4-slim"

# Linting
echo
echo "🐳 Running lints using [${LINT_DOCKER_IMAGE}]"
mkdir -p .ruff_cache

# Only set user mapping if running in GitHub Actions (which will set the ${CI} environment variable)
docker_user_args=()
if [[ "${CI:-}" == "true" ]]; then
    docker_user_args=(-u "$(id -u):$(id -g)")
fi

docker run --rm \
    "${docker_user_args[@]}" \
    -v "${PWD}":/app \
    -w /app \
    -v "${PWD}/.ruff_cache":/app/.ruff_cache \
    "${LINT_DOCKER_IMAGE}" \
    check /app/docker/python/selenium_manager --config ci/python/ruff.toml --fix "$@"

# Tests
echo
echo "🐳 Running tests using [${TEST_DOCKER_IMAGE}]"

docker run --rm -t \
    -v "${PWD}":/app \
    -w /app \
    "${TEST_DOCKER_IMAGE}" \
    bash -c "
    export PYTHONPATH=/app/docker/python &&
    pip install --quiet --upgrade pip --root-user-action=ignore &&
    pip install --quiet -r /app/docker/python/requirements-dev.txt --root-user-action=ignore &&
    pytest -v /app/docker/python/tests
  "
