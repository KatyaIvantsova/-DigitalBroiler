# Цифровой бройлер — ситуационный центр птицефабрики

Веб-платформа мониторинга для птицефабрики: собирает телеметрию с датчиков,
показывает технические показатели по птичникам и партиям, ведёт ленту
уведомлений, реестр инцидентов и задачи для сотрудников.

Продукт состоит из двух приложений:

- **frontend** — дашборд на Next.js
- **backend** — REST API на Spring Boot + инфраструктура (Postgres, InfluxDB, MinIO, Grafana).

---

## Стек технологий

| Слой | Технологии |
|------|------------|
| Frontend | Next.js (App Router), React, TypeScript, Tailwind CSS, Radix UI (shadcn/ui), Recharts |
| Backend | Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, Bean Validation |
| Реляционная БД | PostgreSQL 16 + миграции Flyway |
| Телеметрия (time-series) | InfluxDB 1.8 |
| Хранилище вложений | MinIO (S3-совместимое) |
| Дашборды/графики | Grafana |
| Инфраструктура | Docker Compose |

---

## Структура репозитория

```
├─ frontend/                     # Next.js приложение (дашборд), Dockerfile
│  ├─ app/                       # страницы, /login и API-роуты (app/api/* проксируют на backend)
│  ├─ components/dashboard/      # основные UI-компоненты дашборда
│  ├─ lib/                       # утилиты, клиент к backend (spring-api.ts), auth.ts
│  └─ proxy.ts                   # редирект на /login без сессии
├─ backend/broiler_monitoring/   # Spring Boot приложение, Dockerfile
│  ├─ src/main/java/...          # контроллеры, сервисы, сущности, security/
│  ├─ src/main/resources/
│  │  ├─ application.properties  # конфигурация (секреты только из окружения)
│  │  ├─ db/migration/           # Flyway-миграции
│  │  └─ db/demo/                # демо-данные, только профиль demo
│  ├─ docker-compose.yml         # локальная инфраструктура: Postgres, InfluxDB, Grafana, MinIO
│  └─ .env.example               # шаблон переменных для локальной разработки
├─ deploy.yml                    # прод: nginx + готовые образы backend/frontend + инфраструктура
├─ deploy.env.example            # шаблон .env для сервера
├─ deploy/nginx/                 # конфиг nginx (единственная точка входа)
├─ grafana/provisioning/         # datasource и дашборды Grafana
├─ sensorImitation/              # Python-симулятор датчиков и генератор цикла партии (cycle_generator.py)
└─ docs/sprint1…sprint6/        # аналитика и отчёты по спринтам
```

---

## Требования

- **Node.js** 22 и npm
- **JDK 21**
- **Docker** + Docker Compose

---

## Запуск без установки (GitHub Codespaces)

Ничего ставить на компьютер не нужно, всё работает в браузере:

1. На странице репозитория нажмите **Code → Codespaces → Create codespace on main**.
2. Подождите, пока окружение соберётся (первый раз несколько минут): ставятся Java, Node.js, Docker и зависимости, затем приложение запускается само.
3. Откройте вкладку **PORTS** и порт **3000** «Веб-интерфейс» (обычно он открывается сам).
4. Войдите: логин `admin`, пароль `change-me-local`.

Перезапуск: `bash scripts/dev-up.sh`, остановка: `bash scripts/dev-down.sh`, логи — в папке `logs/`.
Бесплатная квота GitHub — 120 ядро-часов в месяц (около 60 часов на 2-ядерной машине); остановите codespace, когда закончите (Codespaces → Stop).

---

## Быстрый старт (локально)

### 1. Инфраструктура (Docker)

```bash
cd backend/broiler_monitoring
cp .env.example .env           # при желании поменяйте пароли
docker compose up -d           # postgres, influxdb, grafana, minio (порты только на 127.0.0.1)
```

### 2. Backend (Spring Boot)

```bash
cd backend/broiler_monitoring
./mvnw spring-boot:run         # http://localhost:8080, Swagger: /swagger-ui.html
```

Flyway накатит миграции. С `SPRING_PROFILES_ACTIVE=demo` (так в `.env.example`) добавятся демо-инциденты и уведомления.
При первом старте создаётся администратор из `AUTH_BOOTSTRAP_ADMIN_USERNAME` / `AUTH_BOOTSTRAP_ADMIN_PASSWORD`.

### 3. Frontend (Next.js)

```bash
cd frontend
npm ci
npm run dev                    # http://localhost:3000 → /login
```

Фронт ходит на backend по `http://localhost:8080`, переопределяется `SPRING_API_URL`.

### Тесты

```bash
cd backend/broiler_monitoring && ./mvnw verify   # нужен запущенный Docker (Testcontainers)
cd frontend && npm run lint && npx tsc --noEmit && npm run build
```

Те же проверки идут в CI на каждый pull request (`.github/workflows/ci.yml`).

---

## Аутентификация

- Вход по логину и паролю: `POST /api/v1/auth/login` возвращает JWT, фронт хранит его в httpOnly-cookie.
- Без токена API отвечает 401. Роли: OPERATOR, TECHNOLOGIST, VETERINARIAN, MANAGER, ADMIN; матрица прав — [docs/sprint2/04-roles-matrix.md](docs/sprint2/04-roles-matrix.md).
- Пользователей, птичники и датчики заводит администратор в интерфейсе (меню пользователя → «Пользователи», «Птичники и датчики»).
- Датчики и шлюзы пишут `POST /api/v1/telemetry/readings` с заголовком `X-Api-Key: $TELEMETRY_INGEST_API_KEY`.

---

## Переменные окружения

Локально — `backend/broiler_monitoring/.env` (из `.env.example`), на сервере — `.env` рядом с `deploy.yml` (из `deploy.env.example`).
Секреты не имеют значений по умолчанию: без них backend и compose не стартуют.

| Переменная | Назначение |
|------------|------------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | доступ к Postgres |
| `INCIDENT_ATTACHMENTS_S3_*` | MinIO для вложений к инцидентам |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | вход в Grafana |
| `AUTH_JWT_SECRET` | секрет подписи JWT, не короче 32 символов |
| `AUTH_BOOTSTRAP_ADMIN_USERNAME` / `AUTH_BOOTSTRAP_ADMIN_PASSWORD` | первый администратор |
| `TELEMETRY_INGEST_API_KEY` | ключ датчиков и шлюзов |
| `SPRING_PROFILES_ACTIVE` | `demo` — накатить демо-данные |
| `AUTH_COOKIE_SECURE` | `true`, когда сайт работает по HTTPS |
| `SPRING_API_URL` (frontend) | адрес backend |

---

## Порты

| Сервис | Локально | Прод |
|--------|----------|------|
| Frontend | 3000 | через nginx :80 |
| Backend | 8080 | только приём телеметрии через nginx |
| PostgreSQL | 127.0.0.1:5434 | закрыт |
| InfluxDB | 127.0.0.1:8086 | закрыт |
| Grafana | 127.0.0.1:3001 | через nginx /grafana/ |
| MinIO | 127.0.0.1:9000 / 9001 | закрыт |

---

## Прод

- Push в основную ветку собирает образы backend и frontend и кладёт их в GHCR (`.github/workflows/deploy.yml`).
- Деплой по SSH включается переменной репозитория `DEPLOY_ENABLED=true` и секретами `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, `DEPLOY_PATH`.
- На сервере перед первым деплоем: `cp deploy.env.example .env` и заполнить секреты.
  Если тома Postgres, MinIO и Grafana уже существуют, они созданы со старыми паролями: укажите их в `.env` или смените пароли внутри сервисов.
- HTTPS: выпустить сертификат на домен, добавить `server { listen 443 ssl; ... }` в `deploy/nginx/default.conf`, открыть порт 443 и выставить `AUTH_COOKIE_SECURE=true`.

---

## Как это работает

```
Датчики / симулятор ──► Backend (Spring) ──► InfluxDB (телеметрия)
                                     │
                                     ├──► PostgreSQL (уведомления, инциденты, задачи, пользователи)
                                     └──► MinIO (файлы вложений)
                                     ▲
Frontend (Next.js) ── app/api/* ─────┘  (серверные роуты проксируют запросы на backend)
```

- Телеметрия датчиков пишется в **InfluxDB**; по умолчанию включён встроенный
  симулятор (`SENSOR_SIMULATION_ENABLED`), есть и отдельный Python-симулятор в `sensorImitation/`.
  Генератор `sensorImitation/cycle_generator.py` отыгрывает партию целиком: учёт, взвешивания, показания
  и отклонения ([docs/sprint3/09-cycle-generator.md](docs/sprint3/09-cycle-generator.md)).
- **Движок правил** раз в минуту сверяет показания с нормами возраста и кросса (таблицы `norms` и `rules`,
  экран «Нормы и правила») и сам открывает и закрывает инциденты ([docs/sprint3](docs/sprint3/README.md)).
- Пороговые сервисы создают **уведомления** и **инциденты** в Postgres.
- Frontend не обращается к backend напрямую из браузера — запросы идут через
  Next.js API-роуты (`frontend/app/api/*`), которые проксируют на Spring.

---

## Доменные понятия

- **Технические показатели** — KPI по категориям (Освещение, Микроклимат,
  Производственные параметры, Потребление ресурсов, Состояние стада),
  с разбивкой по птичнику, партии и возрастной группе птицы.
- **Уведомления** — сигналы об отклонениях (от датчиков/системы/аналитики).
- **Инциденты** — заведённые в работу проблемы; могут создаваться из уведомлений,
  имеют статус, приоритет, ответственного и историю.
- **Задачи** — поручения сотрудникам, привязанные к показателям.

---

## Миграции базы данных

Flyway-миграции лежат в `backend/broiler_monitoring/src/main/resources/db/migration`
и применяются автоматически при старте backend. Правила:

- новые миграции добавляйте следующим номером версии (`V{N}__описание.sql`);
- демо-данные кладите в `db/demo` (repeatable `R__*.sql`), а не в основные миграции;
- уже применённые миграции **не редактируйте** — Flyway проверяет контрольные суммы;
- префикс версии должен быть заглавной `V` (иначе Flyway файл проигнорирует).



