#!/usr/bin/env bash
# Bring up the self-hosted Convex stack.
#   - Generates .env.docker with a fresh INSTANCE_SECRET on first run.
#   - Waits for the backend health check.
#   - Prints the admin key + relevant URLs.
#
# Usage:
#   scripts/docker-up.sh              # bring up in background
#   scripts/docker-up.sh --fg         # foreground (stream logs)
#   scripts/docker-up.sh --rebuild    # pull latest images first

set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env.docker"
COMPOSE="docker compose --env-file ${ENV_FILE}"

# --- 1. Make sure Docker is running ------------------------------------------
if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker daemon not running. Start Docker Desktop and retry."
  exit 1
fi

# --- 2. Ensure .env.docker exists with a real INSTANCE_SECRET ----------------
if [ ! -f "${ENV_FILE}" ]; then
  echo "📝 First run — creating ${ENV_FILE} from template..."
  cp .env.docker.template "${ENV_FILE}"
  SECRET="$(openssl rand -hex 32)"
  # macOS sed needs '' after -i; Linux needs none. Use a portable Perl fallback.
  perl -i -pe "s|REPLACE_WITH_64_CHAR_HEX|${SECRET}|g" "${ENV_FILE}"
  echo "   Generated INSTANCE_SECRET (stored in ${ENV_FILE})."
fi

if grep -q REPLACE_WITH_64_CHAR_HEX "${ENV_FILE}"; then
  SECRET="$(openssl rand -hex 32)"
  perl -i -pe "s|REPLACE_WITH_64_CHAR_HEX|${SECRET}|g" "${ENV_FILE}"
  echo "   Filled in placeholder INSTANCE_SECRET."
fi

# --- 3. Parse flags -----------------------------------------------------------
REBUILD=0
FG=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --fg)      FG=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

if [ "${REBUILD}" -eq 1 ]; then
  echo "⬇️  Pulling latest images..."
  ${COMPOSE} pull
fi

# --- 4. Start stack -----------------------------------------------------------
if [ "${FG}" -eq 1 ]; then
  ${COMPOSE} up
  exit $?
fi

${COMPOSE} up -d

# --- 5. Wait for backend health ----------------------------------------------
echo -n "⏳ Waiting for Convex backend..."
for i in $(seq 1 60); do
  if ${COMPOSE} ps convex-backend | grep -q "(healthy)"; then
    echo " ✅"
    break
  fi
  sleep 1
  echo -n "."
  if [ "$i" -eq 60 ]; then
    echo " ❌ backend failed to become healthy — see \`docker compose logs convex-backend\`"
    exit 1
  fi
done

# --- 6. Print summary ---------------------------------------------------------
# shellcheck disable=SC1091
source "${ENV_FILE}"

cat <<EOF

🚀 Convex self-hosted stack is up.

   Backend API        http://127.0.0.1:${FITBIT_PORT_CONVEX}
   Site proxy         http://127.0.0.1:${FITBIT_PORT_SITE_PROXY}
   Dashboard          http://127.0.0.1:${FITBIT_PORT_DASHBOARD}
   Android emulator   http://10.0.2.2:${FITBIT_PORT_CONVEX}

EOF

# --- 7. Extract admin key from logs ------------------------------------------
ADMIN_KEY="$(${COMPOSE} logs convex-backend 2>/dev/null | grep -Eo 'convex-self-hosted\|[A-Za-z0-9_-]+' | tail -1 || true)"
if [ -n "${ADMIN_KEY}" ]; then
  echo "🔑 Admin key (used by \`scripts/docker-push.sh\` to deploy functions):"
  echo "   ${ADMIN_KEY}"
  echo
fi

echo "➡ Next: push Convex functions"
echo "   scripts/docker-push.sh            # one-time deploy"
echo "   scripts/docker-push.sh --watch    # watch mode (hot reload on convex/*.ts)"
