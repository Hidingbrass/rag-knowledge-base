#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Checking ignored local files"
git check-ignore -q .env
git check-ignore -q .venv
git check-ignore -q .DS_Store

echo "==> Checking tracked secret-like values"
if git grep -n "DASHSCOPE_API_KEY=.*[A-Za-z0-9_-]\{20,\}" -- . ':!.env.example' ':!docs' ':!README.md'; then
  echo "Potential real DASHSCOPE_API_KEY found in tracked files."
  exit 1
fi

echo "==> Checking whitespace"
git diff --check

echo "==> Checking Docker Compose config"
docker compose config --quiet

echo "==> Running FastAPI tests"
if [ -x "$ROOT_DIR/.venv/bin/python" ]; then
  "$ROOT_DIR/.venv/bin/python" -m pytest -q
else
  python -m pytest -q
fi

echo "==> Running Spring Boot tests"
(
  cd "$ROOT_DIR/springboot-backend"
  mvn -s maven-settings.xml test
)

echo "==> Pre-submit checks passed"
