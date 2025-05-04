#!/bin/bash
set -e

# Create group if not exists
if ! getent group appgroup > /dev/null; then
    groupadd -g "${PGID}" appgroup
fi

# Create user if not exists
if ! id appuser > /dev/null 2>&1; then
    useradd -u "${PUID}" -g "${PGID}" -m -s /bin/bash appuser
fi

# Adjust permissions if needed
chown -R "${PUID}:${PGID}" /app /tmp/chrome || true

# Run the given command as the non-root user
exec gosu appuser "$@"
