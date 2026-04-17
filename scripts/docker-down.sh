#!/usr/bin/env bash
# Stop the self-hosted Convex stack.
#
# Usage:
#   scripts/docker-down.sh            # stop containers, preserve volumes
#   scripts/docker-down.sh --wipe     # also delete the data volume (💣 irreversible)

set -euo pipefail
cd "$(dirname "$0")/.."

WIPE=0
for arg in "$@"; do
  case "$arg" in
    --wipe) WIPE=1 ;;
  esac
done

if [ "${WIPE}" -eq 1 ]; then
  echo "💣 Tearing down stack + deleting data volume..."
  docker compose --env-file .env.docker down --volumes
  echo "   Volume 'fitbit-convex-data' removed. You will need to re-push functions."
else
  docker compose --env-file .env.docker down
  echo "✅ Stack stopped. Data preserved in volume 'fitbit-convex-data'."
  echo "   Bring it back with: scripts/docker-up.sh"
fi
