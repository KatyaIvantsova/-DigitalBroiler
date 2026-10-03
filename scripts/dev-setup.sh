#!/usr/bin/env bash
# Однократная подготовка окружения (Codespaces / dev container): .env и зависимости.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f backend/broiler_monitoring/.env ]; then
  cp backend/broiler_monitoring/.env.example backend/broiler_monitoring/.env
fi

(cd backend/broiler_monitoring && sh mvnw -B -q -DskipTests package)
(cd frontend && npm ci --no-audit --no-fund && npm run build)
