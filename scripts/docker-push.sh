#!/usr/bin/env bash
# Deploy the Convex functions in convex/ to the self-hosted backend.
#
# Reads the admin key from .env.docker (CONVEX_ADMIN_KEY), or generates it
# from the running container if the key isn't persisted yet.
#
# Usage:
#   scripts/docker-push.sh              # one-shot deploy
#   scripts/docker-push.sh --watch      # watch convex/*.ts and hot-reload
#   scripts/docker-push.sh --once       # alias for one-shot

set -euo pipefail
cd "$(dirname "$0")/.."

# --- Load config from .env.docker --------------------------------------------
if [ ! -f .env.docker ]; then
  echo "❌ .env.docker not found — run scripts/docker-up.sh first."
  exit 1
fi
# shellcheck disable=SC1091
set -a; source .env.docker; set +a

BACKEND_URL="http://127.0.0.1:${FITBIT_PORT_CONVEX:-10710}"

# --- Ensure backend is up ----------------------------------------------------
if ! curl -fsS "${BACKEND_URL}/version" >/dev/null 2>&1; then
  echo "❌ Convex backend unreachable at ${BACKEND_URL}"
  echo "   Start it first: scripts/docker-up.sh"
  exit 1
fi

# --- Resolve admin key --------------------------------------------------------
# Priority: CONVEX_ADMIN_KEY from .env.docker > generate from container
ADMIN_KEY="${CONVEX_ADMIN_KEY:-}"

if [ -z "${ADMIN_KEY}" ]; then
  echo "⚠️  CONVEX_ADMIN_KEY not set in .env.docker — generating from container..."
  ADMIN_KEY="$(docker exec fitbit-convex-backend /convex/generate_admin_key.sh 2>/dev/null | grep -v '^Admin key:' || true)"
  ADMIN_KEY="$(echo "$ADMIN_KEY" | tr -d '[:space:]')"
  if [ -z "${ADMIN_KEY}" ]; then
    echo "❌ Could not generate admin key. Is the container running?"
    echo "   Try: docker exec fitbit-convex-backend /convex/generate_admin_key.sh"
    exit 1
  fi
  echo "   Generated: ${ADMIN_KEY:0:30}..."
  echo "   Persisting to .env.docker..."
  echo "" >> .env.docker
  echo "CONVEX_ADMIN_KEY=${ADMIN_KEY}" >> .env.docker
fi

# --- Run the Convex CLI -------------------------------------------------------
export CONVEX_DEPLOY_KEY="${ADMIN_KEY}"

MODE="once"
for arg in "$@"; do
  case "$arg" in
    --watch) MODE="watch" ;;
    --once)  MODE="once" ;;
  esac
done

if [ "${MODE}" = "watch" ]; then
  echo "👀 Watching convex/*.ts → ${BACKEND_URL}"
  echo "   Dashboard: http://127.0.0.1:${FITBIT_PORT_DASHBOARD:-10717}"
  exec npx convex dev --url "${BACKEND_URL}" --admin-key "${ADMIN_KEY}"
else
  echo "📦 Deploying convex/ to ${BACKEND_URL}..."
  npx convex deploy --url "${BACKEND_URL}" --admin-key "${ADMIN_KEY}"
  echo "✅ Deployed. Dashboard: http://127.0.0.1:${FITBIT_PORT_DASHBOARD:-10717}"
fi
