"""Генератор реалистичной партии бройлеров на весь цикл (S3-09).

Сажает партию в птичник «задним числом», заполняет ежедневный учёт (падёж, выбраковка, корм, вода)
и контрольные взвешивания по кривым норм кросса, отправляет показания датчиков птичника
по нормам микроклимата и программе освещения для возраста птицы. По желанию закрывает партию
и подмешивает отклонения, на которых срабатывают правила (температура, «нет данных», падение корма).

Только стандартная библиотека Python 3.10+. Пример:

    python3 sensorImitation/cycle_generator.py --password change-me-local --api-key <TELEMETRY_INGEST_API_KEY>

Параметры — python3 sensorImitation/cycle_generator.py --help.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import random
import sys
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
NORMS_V1 = ROOT / "backend/broiler_monitoring/src/main/resources/norms/norms-v1.csv"
PRODUCTION = ROOT / "docs/sprint2/norms-production.csv"
BATCH = 500


# ---------- нормы ----------

@dataclass
class Band:
    lo: float | None
    target: float | None
    hi: float | None

    def middle(self) -> float:
        if self.target is not None:
            return self.target
        if self.lo is not None and self.hi is not None:
            return (self.lo + self.hi) / 2
        return self.lo if self.lo is not None else (self.hi or 0) * 0.6


def _num(value: str) -> float | None:
    value = value.strip().replace(",", ".")
    return float(value) if value else None


def load_norms(breed: str) -> dict[str, list[tuple[int, int, Band]]]:
    norms: dict[str, list[tuple[int, int, Band]]] = {}
    with NORMS_V1.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle, delimiter=";"):
            if row["breed"] not in ("", breed):
                continue
            band = Band(_num(row["min"]), _num(row["target"]), _num(row["max"]))
            norms.setdefault(row["metric"], []).append((int(row["age_from"]), int(row["age_to"]), band))
    return norms


def norm_for(norms: dict, metric: str, day: int) -> Band | None:
    candidates = [(to - frm, band) for frm, to, band in norms.get(metric, []) if frm <= day <= to]
    return min(candidates, key=lambda item: item[0])[1] if candidates else None


def load_production(breed: str) -> dict[int, dict[str, float]]:
    table: dict[int, dict[str, float]] = {}
    with PRODUCTION.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle, delimiter=";"):
            if row["breed"] == breed:
                table[int(row["day"])] = {key: (_num(value) or 0.0) for key, value in row.items() if key not in ("breed", "day")}
    return table


# ---------- API ----------

class Api:
    def __init__(self, base: str, dry_run: Path | None):
        self.base = base.rstrip("/")
        self.token = ""
        self.dry_run = dry_run
        self.log: list[dict] = []

    def call(self, method: str, path: str, body: dict | None = None, headers: dict | None = None):
        if self.dry_run is not None and method != "GET" and path != "/api/v1/auth/login":
            self.log.append({"method": method, "path": path, "body": body})
            return {"id": f"dry-run-{len(self.log)}"}
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = Request(self.base + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", f"Bearer {self.token}")
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with urlopen(request, timeout=60) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else None
        except HTTPError as error:
            detail = error.read().decode("utf-8", "replace")
            raise SystemExit(f"{method} {path}: HTTP {error.code} {detail[:600]}") from None

    def login(self, username: str, password: str) -> None:
        self.token = self.call("POST", "/api/v1/auth/login", {"username": username, "password": password})["accessToken"]


# ---------- модель партии ----------

def mortality_pct(day: int, base_max: float, rng: random.Random) -> float:
    """Падёж за сутки, % от поголовья: обычно 40–70 % допустимого максимума, в первые дни выше."""
    level = 0.75 if day <= 3 else 0.5
    return max(0.0, base_max * level * rng.uniform(0.6, 1.3))


def light_on(day: int, minute_of_day: int, light_hours: float) -> bool:
    """Тёмный период — одним блоком, начинается в 22:00."""
    dark_minutes = int(round((24 - light_hours) * 60))
    start = 22 * 60
    end = (start + dark_minutes) % (24 * 60)
    if dark_minutes <= 0:
        return True
    in_dark = (start <= minute_of_day or minute_of_day < end) if end < start else (start <= minute_of_day < end)
    return not in_dark


def sensor_value(sensor: dict, day: int, at: datetime, norms: dict, rng: random.Random) -> float | None:
    kind = sensor["type"]
    if kind in ("TEMPERATURE", "HUMIDITY"):
        band = norm_for(norms, kind, day)
        if band is None:
            return None
        spread = 0.35 if kind == "TEMPERATURE" else 2.5
        daily = math.sin((at.hour * 60 + at.minute) / 1440 * 2 * math.pi) * spread
        return round(band.middle() + daily + rng.gauss(0, spread / 3), 1)
    if kind == "CO2":
        return round(rng.uniform(800, 1500) + day * 15)
    if kind == "AMMONIA":
        return round(rng.uniform(2, 5) + day * 0.08, 1)
    if kind == "LIGHT":
        hours = norm_for(norms, "LIGHT_HOURS", day)
        intensity = norm_for(norms, "LIGHT_INTENSITY", day)
        if hours is None or intensity is None:
            return None
        if not light_on(day, at.hour * 60 + at.minute, hours.middle()):
            return 0.0
        return round(intensity.middle() * rng.uniform(0.9, 1.1), 1)
    if kind == "WATER_FLOW":
        return round(rng.uniform(4, 8) + day * 0.25, 1)
    return None


# ---------- сценарий ----------

def main() -> None:
    parser = argparse.ArgumentParser(description="Партия бройлеров целиком: учёт, взвешивания, показания датчиков.")
    parser.add_argument("--api", default="http://localhost:8080", help="адрес backend")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password", required=False, default="change-me-local")
    parser.add_argument("--api-key", default="", help="ключ приёма телеметрии (TELEMETRY_INGEST_API_KEY)")
    parser.add_argument("--house-code", default="1-04", help="код птичника, по умолчанию «Птичник 4»")
    parser.add_argument("--breed", default="ROSS_308", choices=["ROSS_308", "COBB_500"])
    parser.add_argument("--heads", type=int, default=20000, help="посажено голов")
    parser.add_argument("--days", type=int, default=42, help="возраст партии сегодня (длительность цикла)")
    parser.add_argument("--close", action="store_true", help="закрыть партию сегодня (сдача на убой)")
    parser.add_argument("--telemetry-days", type=float, default=2, help="за сколько последних суток отправить показания датчиков")
    parser.add_argument("--step", type=int, default=10, help="шаг показаний датчиков, мин")
    parser.add_argument("--incidents", action="store_true",
                        help="подмешать отклонения: перегрев на 2 °C в последний час, молчащий датчик, падение корма на 15 %% вчера")
    parser.add_argument("--seed", type=int, default=42, help="зерно случайных чисел — одинаковые данные при повторе")
    parser.add_argument("--dry-run", type=Path, help="не отправлять, а записать запросы в JSON-файл")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    norms = load_norms(args.breed)
    production = load_production(args.breed)
    api = Api(args.api, args.dry_run)

    if args.dry_run is None:
        api.login(args.user, args.password)
        houses = api.call("GET", "/api/v1/houses")
        house = next((item for item in houses if item["code"] == args.house_code), None)
        if house is None:
            raise SystemExit(f"Птичник с кодом {args.house_code} не найден")
        sensors = [item for item in api.call("GET", "/api/v1/sensors") if item.get("houseId") == house["id"] and item.get("active")]
    else:
        house = {"id": "dry-run-house", "name": args.house_code}
        sensors = [{"code": f"{kind}-DRY-01", "type": kind, "unit": unit}
                   for kind, unit in (("TEMPERATURE", "C"), ("HUMIDITY", "%"), ("CO2", "ppm"), ("AMMONIA", "ppm"), ("LIGHT", "lux"))]

    today = date.today()
    placed_at = today - timedelta(days=args.days)
    flock = api.call("POST", "/api/v1/flocks", {
        "houseId": house["id"], "breedCode": args.breed, "placedAt": placed_at.isoformat(),
        "placedHeads": args.heads, "placedAvgWeightG": production[0]["body_weight_g"],
        "hatchery": "Генератор S3-09", "targetAgeDays": args.days,
    })
    flock_id = flock["id"]
    print(f"Партия {flock.get('code', flock_id)}: {args.breed}, {args.heads} гол, посадка {placed_at:%d.%m.%Y}")

    alive = args.heads
    performance = rng.uniform(0.95, 1.0)  # площадка обычно немного ниже руководства кросса
    for day in range(0, args.days):
        record_date = placed_at + timedelta(days=day)
        norm = production.get(min(day, max(production)))
        mortality = round(alive * mortality_pct(day, norm["mortality_max_pct_per_day"], rng) / 100)
        culled = round(mortality * rng.uniform(0.15, 0.4))
        feed = alive * norm["feed_intake_g_per_bird"] / 1000 * performance * rng.uniform(0.97, 1.03)
        if args.incidents and day == args.days - 1:
            feed *= 0.85
        water = feed * rng.uniform(1.7, 1.95)
        api.call("POST", f"/api/v1/flocks/{flock_id}/daily-records", {
            "recordDate": record_date.isoformat(), "mortalityHeads": mortality, "culledHeads": culled,
            "feedConsumedKg": round(feed, 1), "waterConsumedL": round(water),
            "comment": "падение потребления корма" if args.incidents and day == args.days - 1 else None,
        })
        alive -= mortality + culled
        if day > 0 and (day % 7 == 0 or day == args.days - 1):
            weight = production[day]["body_weight_g"] * performance * rng.uniform(0.98, 1.02)
            api.call("POST", f"/api/v1/flocks/{flock_id}/weighings", {
                "weighedAt": datetime.combine(record_date, datetime.min.time(), timezone.utc).replace(hour=7).isoformat(),
                "sampleHeads": 100, "avgWeightG": round(weight), "uniformityPct": round(rng.uniform(78, 88), 1), "method": "MANUAL",
            })
    print(f"Учёт за {args.days} сут, живое поголовье {alive} гол")

    readings: list[dict] = []
    now = datetime.now(timezone.utc).replace(second=0, microsecond=0)
    start = now - timedelta(days=args.telemetry_days)
    silent = sensors[-1]["code"] if args.incidents and sensors else None
    hot = next((item["code"] for item in sensors if item["type"] == "TEMPERATURE"), None) if args.incidents else None
    at = start
    while at <= now:
        day = (at.date() - placed_at).days
        for sensor in sensors:
            if sensor["code"] == silent and at > now - timedelta(minutes=45):
                continue
            value = sensor_value(sensor, day, at, norms, rng)
            if value is None:
                continue
            if sensor["code"] == hot and at > now - timedelta(minutes=50):
                value = round(value + 2.2, 1)
            readings.append({"sensorCode": sensor["code"], "type": sensor["type"], "value": value, "unit": sensor["unit"],
                             "measuredAt": at.isoformat().replace("+00:00", "Z")})
        at += timedelta(minutes=args.step)
    headers = {"X-Api-Key": args.api_key} if args.api_key else None
    for index in range(0, len(readings), BATCH):
        api.call("POST", "/api/v1/telemetry/readings",
                 {"gatewayId": "GW-CYCLE-GENERATOR", "readings": readings[index:index + BATCH]}, headers)
    print(f"Показаний датчиков: {len(readings)} ({len(sensors)} датчиков, {args.telemetry_days} сут, шаг {args.step} мин)")

    if args.close:
        weight = production[min(args.days, max(production))]["body_weight_g"] * performance / 1000
        shipped = alive - rng.randint(0, 30)
        api.call("POST", f"/api/v1/flocks/{flock_id}/close", {
            "closedAt": today.isoformat(), "shippedHeads": shipped, "shippedLiveWeightKg": round(shipped * weight),
        })
        print(f"Партия закрыта: сдано {shipped} гол, средний вес {weight:.2f} кг")

    if args.dry_run is not None:
        args.dry_run.write_text(json.dumps(api.log, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"Запросы записаны в {args.dry_run} ({len(api.log)} шт.)")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
