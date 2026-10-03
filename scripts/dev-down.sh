#!/usr/bin/env bash
# Останавливает backend, frontend и контейнеры инфраструктуры.
set -uo pipefail
cd "$(dirname "$0")/.."
pkill -f "broiler_monitoring-.*\.jar" 2>/dev/null
pkill -f "standalone/server.js" 2>/dev/null
(cd backend/broiler_monitoring && docker compose down)
