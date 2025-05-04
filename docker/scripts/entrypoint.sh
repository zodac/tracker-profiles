#!/bin/bash
# ------------------------------------------------------------------------------
# Script to ensure a specific user and group exist, then run a command as that user.
#
# - Creates a group named 'appgroup' with GID specified by $PGID if it doesn't exist
# - Creates a user named 'appuser' with UID $PUID and GID $PGID if it doesn't exist
# - Executes the given command as 'appuser' using gosu
# ------------------------------------------------------------------------------
set -euo pipefail

# Create group if not exists
if ! getent group appgroup > /dev/null; then
    groupadd -g "${PGID}" appgroup
fi

# Create user if not exists
if ! id appuser > /dev/null 2>&1; then
    useradd -u "${PUID}" -g "${PGID}" -m -s /bin/bash appuser
fi

# Run the given command as the non-root user
exec gosu appuser "$@"
