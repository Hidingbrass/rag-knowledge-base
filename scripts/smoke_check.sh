#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:6333}"

check_url() {
  local name="$1"
  local url="$2"

  echo "==> Checking ${name}: ${url}"
  curl -fsS --max-time 8 "${url}" >/dev/null
}

echo "Smoke check does not call DashScope or write demo data."
echo "Backend URL: ${BACKEND_URL}"
echo "FastAPI: Compose internal network only"
echo "Qdrant URL: ${QDRANT_URL}"
echo

check_url "Spring Boot health" "${BACKEND_URL}/api/health"
check_url "Vue3 workspace" "${BACKEND_URL}/index.html"
check_url "debug page" "${BACKEND_URL}/debug.html"
check_url "Qdrant collections" "${QDRANT_URL}/collections"

echo "==> Checking FastAPI through the Compose internal network"
docker compose exec -T api python -c '
import os
import urllib.request

key = os.environ["FASTAPI_API_KEY"]
for path in ("/health", "/qdrant/health"):
    request = urllib.request.Request(
        "http://127.0.0.1:8000" + path,
        headers={"X-API-Key": key},
    )
    urllib.request.urlopen(request, timeout=8).read()
'

echo
echo "Smoke check passed. For the real AI job-agent demo, run: bash scripts/demo_job_agent.sh"
