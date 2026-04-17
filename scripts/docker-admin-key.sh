#!/usr/bin/env bash
# Print the self-hosted Convex admin key.
# Useful for pasting into the dashboard's "Admin key" prompt.
#
# Priority: reads from .env.docker if persisted, otherwise generates
# from the running container using /convex/generate_admin_key.sh.

set -euo pipefail
cd "$(dirname "$0")/.."

# Try .env.docker first
if [ -f .env.docker ]; then
  # shellcheck disable=SC1091
  set -a; source .env.docker; set +a
  if [ -n "${CONVEX_ADMIN_KEY:-}" ]; then
    echo "${CONVEX_ADMIN_KEY}"
    exit 0
  fi
fi

# Fall back to generating from container
KEY="$(docker exec fitbit-convex-backend /convex/generate_admin_key.sh 2>&1 | tail -1)"
if [ -z "${KEY}" ]; then
  echo "❌ Could not get admin key. Is the container running?"
  echo "   docker compose --env-file .env.docker ps"
  exit 1
fi

echo "${KEY}"
