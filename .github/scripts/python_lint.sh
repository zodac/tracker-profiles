#!/bin/bash
set -euo pipefail

docker run --rm \
    -v "${PWD}":/app \
    -w /app \
    -v "${PWD}/.ruff_cache":/app/.ruff_cache \
    ghcr.io/astral-sh/ruff \
    check . --config ci/python/ruff.toml --fix "$@"
