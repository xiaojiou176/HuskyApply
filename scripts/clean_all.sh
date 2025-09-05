#!/usr/bin/env bash
set -euo pipefail

echo "[clean] python caches"
find brain -type d -name "__pycache__" -prune -exec rm -rf {} + 2>/dev/null || true
rm -rf brain/.venv brain/.pytest_cache brain/.mypy_cache brain/.ruff_cache brain/build brain/dist brain/pip-wheel-metadata 2>/dev/null || true

echo "[clean] java/gradle/maven"
find gateway -type d -name "target" -prune -exec rm -rf {} + 2>/dev/null || true
find gateway -type d -name "build"  -prune -exec rm -rf {} + 2>/dev/null || true
rm -rf ops/infra/proto/build .gradle .mvn 2>/dev/null || true

echo "[clean] frontend"
rm -rf frontend/node_modules frontend/dist frontend/.cache 2>/dev/null || true

echo "[clean] coverage/logs"
rm -rf coverage .coverage* htmlcov 2>/dev/null || true
find . -type f -name "*.log" -delete 2>/dev/null || true

echo "[clean] docker (optional)"
if command -v docker >/dev/null 2>&1; then
  docker compose -f ops/infra/docker-compose.yml down -v --remove-orphans 2>/dev/null || true
fi

echo "[done] workspace is cleaned."