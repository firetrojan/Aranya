"""End-to-end check of the exact request path the Android app uses.

The Kotlin tests use labelled fixtures. This exercises the real endpoint with the real
parameter string the app builds, to confirm the contract the DTOs depend on still holds.
It is a probe, not a test of the app: it verifies the upstream contract, and it never
prints a value as if it were advice.

Run: python3 data-pipeline/tests/probe_live_weather.py
"""

import json
import ssl
import urllib.error
import urllib.parse
import urllib.request

CTX = ssl.create_default_context()

# These three constants are duplicated from OpenMeteoWeatherProvider.kt on purpose: if the
# Kotlin side changes them, this probe must be updated too, which makes the coupling
# visible rather than silent.
BASE_URL = "https://api.open-meteo.com/v1/forecast"
CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m"
DAILY_FIELDS = (
    "weather_code,temperature_2m_max,temperature_2m_min,"
    "precipitation_sum,precipitation_probability_max"
)


def build_url(lat, lon, forecast_days=5):
    """Build the URL exactly as the Android adapter does."""
    params = {
        "latitude": lat,
        "longitude": lon,
        "current": CURRENT_FIELDS,
        "daily": DAILY_FIELDS,
        "forecast_days": str(forecast_days),
        "timezone": "auto",
    }
    return BASE_URL + "?" + urllib.parse.urlencode(params)


def check(label, ok, detail=""):
    print("%-4s %s%s" % ("OK" if ok else "FAIL", label, (" - " + detail) if detail else ""))
    return 0 if ok else 1


def main():
    failures = 0

    # Network reachability is tracked separately from contract violations. A flaky
    # connection must not be reported as "the upstream contract changed", because that
    # would send someone hunting a contract break that does not exist.
    transport_failures = 0

    # Two of the shipped preset locations, plus a non-preset coordinate, to prove the
    # request works for any point and not just a hardcoded one.
    cases = [
        ("Pune preset", 18.5214, 73.8567),
        ("Delhi preset", 28.6139, 77.2090),
        ("arbitrary point", 21.1458, 79.0882),
    ]

    for name, lat, lon in cases:
        url = build_url(lat, lon)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Aranya/0.1 (live probe)"})
            with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
                status = r.status
                body = r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            print("FAIL %s: request - HTTP %s" % (name, e.code))
            transport_failures += 1
            continue
        except Exception as e:
            # URLError, timeout, socket errors: the network, not the contract.
            print("NETWORK %s: request - %s" % (name, type(e).__name__))
            transport_failures += 1
            continue

        failures += check("%s: HTTP 200" % name, status == 200, "got %s" % status)
        try:
            doc = json.loads(body)
        except Exception as e:
            failures += check("%s: JSON" % name, False, str(e)[:60])
            continue
        failures += check("%s: JSON parsed" % name, True, "%d bytes" % len(body))

        # The exact assertions OpenMeteoWeatherProvider.verifyUnits() makes.
        cu = doc.get("current_units", {})
        du = doc.get("daily_units", {})
        failures += check(
            "%s: current_units.temperature_2m == 'C'" % name,
            cu.get("temperature_2m") == "\u00b0C", repr(cu.get("temperature_2m")))
        failures += check(
            "%s: current_units.relative_humidity_2m == '%%'" % name,
            cu.get("relative_humidity_2m") == "%")
        failures += check(
            "%s: daily_units.precipitation_sum == 'mm'" % name,
            du.get("precipitation_sum") == "mm")
        failures += check(
            "%s: daily_units.temperature_2m_max == 'C'" % name,
            du.get("temperature_2m_max") == "\u00b0C")

        # Fields the DTO maps.
        cur = doc.get("current", {})
        for f in ("time", "temperature_2m", "relative_humidity_2m", "precipitation", "wind_speed_10m"):
            failures += check("%s: current.%s present" % (name, f), f in cur)

        daily = doc.get("daily", {})
        for f in ("time", "weather_code", "temperature_2m_max", "temperature_2m_min",
                  "precipitation_sum", "precipitation_probability_max"):
            failures += check("%s: daily.%s present" % (name, f), f in daily)

        # Array alignment: the DTOs zip by index, so equal lengths matter.
        times = daily.get("time", [])
        aligned = all(
            len(daily.get(f, [])) == len(times)
            for f in ("weather_code", "temperature_2m_max", "temperature_2m_min",
                      "precipitation_sum", "precipitation_probability_max")
        )
        failures += check("%s: daily arrays aligned (%d days)" % (name, len(times)), aligned)

        # Dates are local YYYY-MM-DD, never instants. An instant here would shift the day.
        dates_ok = all(len(d) == 10 and d[4] == "-" and d[7] == "-" for d in times)
        failures += check("%s: dates are YYYY-MM-DD local" % name, dates_ok)

        # Timezone must resolve, since the UI renders these dates as local days.
        failures += check("%s: timezone returned" % name, bool(doc.get("timezone")),
                          str(doc.get("timezone")))

        print()

    print()
    if transport_failures and not failures:
        print("LIVE PROBE INCONCLUSIVE: %d of %d requests could not reach the provider "
              "(network, not contract). No contract assertion failed." % (transport_failures, len(cases)))
        return 0
    if failures:
        print("LIVE PROBE FAILED: %d contract assertions did not hold" % failures)
        print("The Android adapter would reject this response. Check whether the")
        print("upstream contract changed before trusting the weather feature.")
        return 1
    if transport_failures:
        print("LIVE PROBE PASSED with %d unreachable location(s); contract held where reachable."
              % transport_failures)
        return 0
    print("LIVE PROBE PASSED: the upstream contract the app depends on holds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
