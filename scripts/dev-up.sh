#!/usr/bin/env bash
# Поднимает всё приложение одной командой: инфраструктура в Docker, backend и frontend в фоне.
# Логи: logs/backend.log, logs/frontend.log. Остановить: bash scripts/dev-down.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
mkdir -p logs

cd "$ROOT/backend/broiler_monitoring"
[ -f .env ] || cp .env.example .env
docker compose up -d postgres influxdb grafana
# MinIO нужен только для вложений к инцидентам: если образ не скачался, остальное всё равно работает
docker compose up -d minio minio-init || echo "MinIO не запустился: вложения к инцидентам работать не будут"

# Ждём Postgres
for _ in $(seq 1 60); do
  docker compose exec -T postgres pg_isready >/dev/null 2>&1 && break
  sleep 2
done

JAR=$(ls target/broiler_monitoring-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  sh mvnw -B -q -DskipTests package
  JAR=$(ls target/broiler_monitoring-*.jar | head -n 1)
fi
pkill -f "broiler_monitoring-.*\.jar" 2>/dev/null || true
setsid nohup java -jar "$JAR" > "$ROOT/logs/backend.log" 2>&1 < /dev/null &

cd "$ROOT/frontend"
[ -f .next/standalone/server.js ] || { npm ci --no-audit --no-fund; npm run build; }
cp -r public .next/standalone/ 2>/dev/null || true
mkdir -p .next/standalone/.next && cp -r .next/static .next/standalone/.next/
pkill -f "standalone/server.js" 2>/dev/null || true
(cd .next/standalone && SPRING_API_URL=http://localhost:8080 PORT=3000 HOSTNAME=0.0.0.0 \
  setsid nohup node server.js > "$ROOT/logs/frontend.log" 2>&1 < /dev/null &)

echo "Ждём backend..."
for _ in $(seq 1 90); do
  curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1 && break
  sleep 2
done

echo
echo "Готово: веб-интерфейс на порту 3000 (вкладка PORTS), логин admin, пароль из backend/broiler_monitoring/.env"
