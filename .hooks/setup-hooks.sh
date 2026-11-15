#!/bin/bash
# Commit message format
cp ./ci/hooks/commit-msg.sh .git/hooks/commit-msg
chmod +x .git/hooks/commit-msg

# Pre-commit build validation
cp ./ci/hooks/pre-commit.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

echo "Git hooks installed!"
