"""Capture the EXACT Open-Meteo response shape the Android adapter will parse.

This is a recorded probe, not a guess: the Kotlin DTOs are written against whatever this
prints, so a field-name change in the upstream API shows up as a parsing failure rather
than as silent nonsense in the app.

Run: python3 data-pipeline/tests/probe_openmeteo_contract.py
"""

import json
import ssl
import urllib.parse
import urllib.request

CTX = ssl.create_default_context()
BASE = "https://api.open-meteo.com/v1/forecast"

# Exactly the request the Android adapter will make.
PARAMS = {
    "latitude": "18.5214",
    "longitude": "73.8567",
    "current": "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m",
    "daily": (
        "weather_code,temperature_2m_max,temperature_2m_min,"
        "precipitation_sum,precipitation_probability_max"
    ),
    "forecast_days": "5",
    "timezone": "auto",
}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Aranya/0.1 (contract probe)"})
    with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
        return r.status, dict(r.headers), r.read().decode("utf-8")


def main():
    url = BASE + "?" + urllib.parse.urlencode(PARAMS)
    status, headers, body = get(url)

    print("URL: %s" % url)
    print("status: %s" % status)
    print("content-type: %s" % headers.get("Content-Type"))
    print("bytes: %d" % len(body))
    print()

    doc = json.loads(body)

    print("top-level keys:")
    for k in sorted(doc.keys()):
        v = doc[k]
        kind = type(v).__name__
        print("  %-14s %s" % (k, kind))
    print()

    print("current keys : %s" % sorted(doc.get("current", {}).keys()))
    print("current_units: %s" % json.dumps(doc.get("current_units"), sort_keys=True))
    print("current      : %s" % json.dumps(doc.get("current"), sort_keys=True))
    print()
    print("daily keys   : %s" % sorted(doc.get("daily", {}).keys()))
    print("daily_units  : %s" % json.dumps(doc.get("daily_units"), sort_keys=True))
    daily = doc.get("daily", {})
    print("daily dates  : %s" % daily.get("time"))
    for field in ("weather_code", "temperature_2m_max", "temperature_2m_min",
                  "precipitation_sum", "precipitation_probability_max"):
        print("  %-32s %s" % (field, daily.get(field)))
    print()
    print("timezone      : %s" % doc.get("timezone"))
    print("utc_offset_s  : %s" % doc.get("utc_offset_seconds"))
    print("elevation_m   : %s" % doc.get("elevation"))
    print("latitude_echo : %s" % doc.get("latitude"))
    print("longitude_echo: %s" % doc.get("longitude"))

    # Contract assertions the Kotlin parser depends on. If one fails, the DTO is wrong.
    print()
    print("=== contract checks ===")
    problems = []

    for key in ("current", "current_units", "daily", "daily_units", "timezone"):
        if key not in doc:
            problems.append("missing top-level key: %s" % key)

    for key in ("time", "temperature_2m", "relative_humidity_2m"):
        if key not in doc.get("current", {}):
            problems.append("current is missing: %s" % key)

    for key in ("time", "weather_code", "temperature_2m_max", "temperature_2m_min",
                "precipitation_sum", "precipitation_probability_max"):
        if key not in doc.get("daily", {}):
            problems.append("daily is missing: %s" % key)

    # Unit contract: the adapter relies on these exact unit strings.
    units = doc.get("current_units", {})
    if units.get("temperature_2m") != "°C":
        problems.append("current_units.temperature_2m is %r, expected '°C'" % units.get("temperature_2m"))
    if units.get("relative_humidity_2m") != "%":
        problems.append("current_units.relative_humidity_2m is %r, expected '%%'" % units.get("relative_humidity_2m"))

    dunits = doc.get("daily_units", {})
    if dunits.get("precipitation_sum") != "mm":
        problems.append("daily_units.precipitation_sum is %r, expected 'mm'" % dunits.get("precipitation_sum"))

    # Length contract: daily arrays must all align with the time array.
    times = doc.get("daily", {}).get("time", [])
    for field in ("weather_code", "temperature_2m_max", "temperature_2m_min",
                  "precipitation_sum", "precipitation_probability_max"):
        values = doc.get("daily", {}).get(field)
        if values is not None and len(values) != len(times):
            problems.append("daily.%s has %d entries but daily.time has %d" % (field, len(values), len(times)))

    # Dates must be ISO local dates, not instants.
    for d in times:
        if len(d) != 10 or d[4] != "-" or d[7] != "-":
            problems.append("daily date %r is not YYYY-MM-DD" % d)
            break

    if problems:
        for p in problems:
            print("  PROBLEM: %s" % p)
        return 1
    print("  all contract checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
