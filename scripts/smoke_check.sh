#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
API_URL="${API_URL:-http://127.0.0.1:8000}"
QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:6333}"

check_url() {
  local name="$1"
  local url="$2"

  echo "==> Checking ${name}: ${url}"
  curl -fsS --max-time 8 "${url}" >/dev/null
}

echo "Smoke check does not call DashScope or write demo data."
echo "Backend URL: ${BACKEND_URL}"
echo "FastAPI URL: ${API_URL}"
echo "Qdrant URL: ${QDRANT_URL}"
echo

check_url "Spring Boot health" "${BACKEND_URL}/api/health"
check_url "Vue3 workspace" "${BACKEND_URL}/index.html"
check_url "debug page" "${BACKEND_URL}/debug.html"
check_url "FastAPI health" "${API_URL}/health"
check_url "FastAPI Swagger" "${API_URL}/docs"
check_url "FastAPI Qdrant health" "${API_URL}/qdrant/health"
check_url "Qdrant collections" "${QDRANT_URL}/collections"

echo
echo "Smoke check passed. For the real AI job-agent demo, run: bash scripts/demo_job_agent.sh"
