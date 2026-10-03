import json
import os
import random
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BACKEND_URL = "http://localhost:8080/api/v1/telemetry/readings"
BACKEND_URL = os.getenv("BACKEND_URL", DEFAULT_BACKEND_URL)
GATEWAY_ID = os.getenv("GATEWAY_ID", "GW-FARM-1-HOUSE-4")
INTERVAL_SECONDS = int(os.getenv("INTERVAL_SECONDS", "600"))
# Ключ приёма телеметрии (TELEMETRY_INGEST_API_KEY на backend), уходит в заголовке X-Api-Key
API_KEY = os.getenv("TELEMETRY_INGEST_API_KEY", "")


SENSORS = [
    {
        "sensorCode": "TEMP-HOUSE-4-01",
        "type": "TEMPERATURE",
        "unit": "C",
        "min": 28.5,
        "max": 35.5,
        "precision": 1,
    },
    {
        "sensorCode": "HUM-HOUSE-4-01",
        "type": "HUMIDITY",
        "unit": "%",
        "min": 55.0,
        "max": 85.0,
        "precision": 1,
    },
    {
        "sensorCode": "CO2-HOUSE-4-01",
        "type": "CO2",
        "unit": "ppm",
        "min": 850,
        "max": 1900,
        "precision": 0,
    },
    {
        "sensorCode": "AMMONIA-HOUSE-4-01",
        "type": "AMMONIA",
        "unit": "ppm",
        "min": 8.0,
        "max": 24.0,
        "precision": 1,
    },
    {
        "sensorCode": "WATER-HOUSE-4-01",
        "type": "WATER_FLOW",
        "unit": "l/min",
        "min": 8.0,
        "max": 18.0,
        "precision": 1,
    },
    {
        "sensorCode": "FCR-HOUSE-4-01",
        "type": "FEED_CONVERSION",
        "unit": "kg/kg",
        "min": 1.35,
        "max": 1.95,
        "precision": 2,
    },
    {
        "sensorCode": "WEIGHT-HOUSE-4-01",
        "type": "WEIGHT",
        "unit": "g",
        "min": 950,
        "max": 2450,
        "precision": 0,
    },
    {
        "sensorCode": "FEED-HOUSE-4-01",
        "type": "FEED_CONSUMPTION",
        "unit": "kg/h",
        "min": 35.0,
        "max": 82.0,
        "precision": 1,
    },
    {
        "sensorCode": "MORTALITY-HOUSE-4-01",
        "type": "MORTALITY",
        "unit": "birds",
        "min": 0,
        "max": 7,
        "precision": 0,
    },
]


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def generate_value(sensor: dict) -> float | int:
    if sensor["precision"] == 0:
        return random.randint(int(sensor["min"]), int(sensor["max"]))

    value = random.uniform(float(sensor["min"]), float(sensor["max"]))
    return round(value, int(sensor["precision"]))


def build_payload() -> dict:
    measured_at = utc_now_iso()

    return {
        "gatewayId": GATEWAY_ID,
        "readings": [
            {
                "sensorCode": sensor["sensorCode"],
                "type": sensor["type"],
                "value": generate_value(sensor),
                "unit": sensor["unit"],
                "measuredAt": measured_at,
            }
            for sensor in SENSORS
        ],
    }


def send_payload(payload: dict) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if API_KEY:
        headers["X-Api-Key"] = API_KEY
    request = Request(
        BACKEND_URL,
        data=body,
        headers=headers,
        method="POST",
    )

    with urlopen(request, timeout=15) as response:
        response_body = response.read().decode("utf-8")
        print(f"[OK] {datetime.now().isoformat(timespec='seconds')} status={response.status} body={response_body}")


def run() -> None:
    print(f"Sensor simulator started")
    print(f"Backend URL: {BACKEND_URL}")
    print(f"Gateway ID: {GATEWAY_ID}")
    print(f"Interval: {INTERVAL_SECONDS} seconds")

    while True:
        payload = build_payload()

        try:
            send_payload(payload)
        except HTTPError as error:
            error_body = error.read().decode("utf-8")
            print(f"[HTTP ERROR] status={error.code} body={error_body}")
        except URLError as error:
            print(f"[NETWORK ERROR] {error.reason}")
        except Exception as error:
            print(f"[ERROR] {error}")

        time.sleep(INTERVAL_SECONDS)


if __name__ == "__main__":
    run()
