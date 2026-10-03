#!/usr/bin/env bash
# Однократная подготовка окружения (Codespaces / dev container): .env и зависимости.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f backend/broiler_monitoring/.env ]; then
  cp backend/broiler_monitoring/.env.example backend/broiler_monitoring/.env
fi

(cd backend/broiler_monitoring && sh mvnw -B -q -DskipTests package)
# npm ci повторяется: при обрыве сети (ECONNRESET) обычно помогает вторая попытка
(cd frontend && for attempt in 1 2 3; do npm ci --no-audit --no-fund && break; [ "$attempt" = 3 ] && exit 1; echo "npm ci: попытка $attempt не удалась, повторяем..."; sleep 10; done && npm run build)
