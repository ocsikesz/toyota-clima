#!/usr/bin/env python3
"""Pornește climatizarea Toyota în funcție de temperatura de afară.

Rulare:
    python toyota_clima.py              # verifică vremea și trimite comanda
    python toyota_clima.py --dry-run    # doar afișează ce ar face, nu trimite nimic
    python toyota_clima.py --temp 3     # testează logica cu o temperatură dată
    python toyota_clima.py --stop       # oprește climatizarea

Programare (cron, luni-vineri):
    20 6  * * 1-5  cd /cale/toyota_clima && python toyota_clima.py >> clima.log 2>&1
    30 14 * * 1-5  cd /cale/toyota_clima && python toyota_clima.py >> clima.log 2>&1
"""

import argparse
import asyncio
import json
import sys
from datetime import datetime
from pathlib import Path

import httpx
from pytoyoda.client import MyT
from pytoyoda.models.endpoints.climate import (
    HeatingOptionsModel,
    V2RemoteClimateControlRequestModel,
)
from pytoyoda.models.endpoints.common import UnitValueModel

BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "config.json"

DEFAULTS = {
    "brand": "T",
    # Târgu Mureș
    "latitude": 46.54,
    "longitude": 24.56,
    # Sub această temperatură afară -> încălzire
    "heat_below": 12.0,
    # Peste această temperatură afară -> răcire
    "cool_above": 24.0,
    # Temperatura țintă în habitaclu
    "heat_target": 22.0,
    "cool_target": 20.0,
    # Sub această temperatură se pornește și degivrarea parbrizului și a lunetei
    "defrost_below": 3.0,
    # Durata climatizării, în minute
    "duration": 10,
}


def log(msg: str) -> None:
    print(f"[{datetime.now():%Y-%m-%d %H:%M:%S}] {msg}", flush=True)


def load_config() -> dict:
    if not CONFIG_FILE.exists():
        sys.exit(f"Lipsește {CONFIG_FILE.name}. Copiază config.example.json și completează-l.")
    with CONFIG_FILE.open(encoding="utf-8") as f:
        user = json.load(f)
    cfg = {**DEFAULTS, **user}
    if not cfg.get("username") or not cfg.get("password"):
        sys.exit("Completează username și password în config.json.")
    return cfg


def outside_temperature(lat: float, lon: float) -> float:
    """Temperatura curentă de la Open-Meteo (gratuit, fără cheie API)."""
    resp = httpx.get(
        "https://api.open-meteo.com/v1/forecast",
        params={"latitude": lat, "longitude": lon, "current": "temperature_2m"},
        timeout=15,
    )
    resp.raise_for_status()
    return float(resp.json()["current"]["temperature_2m"])


def decide(temp: float, cfg: dict) -> V2RemoteClimateControlRequestModel | None:
    """Alege încălzire, răcire sau nimic, în funcție de temperatură."""
    if temp < cfg["heat_below"]:
        target = cfg["heat_target"]
        defrost = "on" if temp < cfg["defrost_below"] else "off"
        heating = HeatingOptionsModel(front_defroster=defrost, rear_defogger=defrost)
        log(f"Afară {temp:.1f}°C -> ÎNCĂLZIRE la {target}°C, degivrare: {defrost}")
    elif temp > cfg["cool_above"]:
        target = cfg["cool_target"]
        heating = HeatingOptionsModel(front_defroster="off", rear_defogger="off")
        log(f"Afară {temp:.1f}°C -> RĂCIRE la {target}°C")
    else:
        log(
            f"Afară {temp:.1f}°C, între {cfg['heat_below']} și {cfg['cool_above']}°C "
            "-> nu pornesc nimic"
        )
        return None

    return V2RemoteClimateControlRequestModel(
        command="start",
        duration=int(cfg["duration"]),
        temperature=UnitValueModel(value=target, unit="C"),
        heating_options=heating,
        save_settings=False,
    )


async def send(cfg: dict, request: V2RemoteClimateControlRequestModel) -> bool:
    client = MyT(
        username=cfg["username"],
        password=cfg["password"],
        use_metric=True,
        brand=cfg["brand"],
    )
    await client.login()
    cars = await client.get_vehicles()
    if not cars:
        log("Nicio mașină găsită în contul MyToyota.")
        return False

    vin_wanted = cfg.get("vin")
    car = next((c for c in cars if not vin_wanted or c.vin == vin_wanted), None)
    if car is None:
        log(f"Nu găsesc mașina cu VIN {vin_wanted}.")
        return False

    resp = await car.set_climate(request)
    code = resp.payload.return_code if resp.payload else None
    if code == "000000":
        log(f"Comandă '{request.command}' acceptată pentru {car.alias or car.vin}.")
        return True

    # Coduri de eroare cunoscute: mașina descuiată, o ușă deschisă,
    # cheia în mașină sau climatizarea deja pornită.
    log(f"Comandă respinsă, cod {code}. Verifică: mașina încuiată, ușile închise, "
        "cheia nu e în mașină, climatizarea nu e deja pornită.")
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Climatizare Toyota după vreme")
    parser.add_argument("--dry-run", action="store_true", help="nu trimite comanda")
    parser.add_argument("--temp", type=float, help="folosește această temperatură în loc de vreme")
    parser.add_argument("--stop", action="store_true", help="oprește climatizarea")
    args = parser.parse_args()

    if args.dry_run and args.temp is not None:
        cfg = {**DEFAULTS, **(json.loads(CONFIG_FILE.read_text()) if CONFIG_FILE.exists() else {})}
    else:
        cfg = load_config()

    if args.stop:
        request = V2RemoteClimateControlRequestModel(command="stop")
        log("Opresc climatizarea.")
    else:
        try:
            temp = args.temp if args.temp is not None else outside_temperature(
                cfg["latitude"], cfg["longitude"]
            )
        except Exception as ex:  # noqa: BLE001
            log(f"Nu pot citi vremea: {ex}")
            return 1
        request = decide(temp, cfg)
        if request is None:
            return 0

    if args.dry_run:
        payload = request.model_dump(by_alias=True, exclude_none=True)
        log(f"DRY RUN, aș trimite: {json.dumps(payload, ensure_ascii=False)}")
        return 0

    try:
        ok = asyncio.run(send(cfg, request))
    except Exception as ex:  # noqa: BLE001
        log(f"Eroare Toyota API: {type(ex).__name__}: {ex}")
        return 1
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
