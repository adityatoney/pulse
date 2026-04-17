#!/usr/bin/env bash
# Start a local Convex dev deployment on the ports assigned in the port registry.
# Registry: Fitbit is project #7, base 10710.
#   - 10710  Convex backend API
#   - 10715  Site proxy
#   - 10717  Local dashboard UI

set -euo pipefail

# Load .env.local if present (bash-friendly form — one KEY=VALUE per line).
if [ -f .env.local ]; then
  set -a
  # shellcheck disable=SC1091
  source .env.local
  set +a
fi

PORT_API="${FITBIT_PORT_CONVEX:-10710}"
PORT_SITE="${FITBIT_PORT_SITE_PROXY:-10715}"
PORT_DASH="${FITBIT_PORT_DASHBOARD:-10717}"

echo "Starting Convex dev (local)..."
echo "  API           http://127.0.0.1:${PORT_API}"
echo "  Site proxy    http://127.0.0.1:${PORT_SITE}"
echo "  Dashboard     http://127.0.0.1:${PORT_DASH}"
echo "  Android URL   http://10.0.2.2:${PORT_API}   (emulator)"
echo

# Run the Convex CLI in local mode with our ports.
# Note: --local runs self-hosted; drop --local for cloud dev deploy.
npx convex dev \
  --local \
  --local-port "${PORT_API}" \
  --local-site-port "${PORT_SITE}" \
  --local-dashboard-port "${PORT_DASH}" \
  "$@"
