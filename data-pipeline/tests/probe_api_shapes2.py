"""Second-round probe: confirm SoilGrids property names and additional soil/policy sources.

SoilGrids returned an empty `layers` array on the first attempt, so the property and
depth parameter names must be confirmed before any soil value is claimed usable.
"""
import json
import ssl
import urllib.error
import urllib.request

CTX = ssl.create_default_context()
TIMEOUT = 25

API_PROBES = [
    ("soilgrids_ph_depth_cm", "GET",
     "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=72.87&lat=19.07"
     "&property=phh2o&depth=0-5cm&value=mean&units=ph", None, {}),
    ("soilgrids_sand_clay", "GET",
     "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=72.87&lat=19.07"
     "&property=clay&sand&silt&depth=0-30cm&value=mean", None, {}),
    ("soilgrids_bulk_density", "GET",
     "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=72.87&lat=19.07"
     "&property=bdod&depth=0-5cm&value=mean", None, {}),
    ("soil_health_card", "GET", "https://soilhealth.dac.gov.in/", None, {}),
    ("kisan_suvidha", "GET", "https://www.kisansuvidha.gov.in/", None, {}),
    ("farmer_portal", "GET", "https://farmer.gov.in/", None, {}),
    ("enam", "GET", "https://enam.gov.in/", None, {}),
    ("imd_api_root", "GET", "https://api.imd.gov.in/", None, {}),
    ("imd_openend_docs", "GET", "https://mausam.imd.gov.in/", None, {}),
    ("worldbank_agro_climate", "GET",
     "https://climate-api.worldbank.org/v2/country/IND/indicator/AG.LND.PRCP.MM?format=json&per_page=2",
     None, {}),
    ("openmeteo_air_quality", "GET",
     "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=19.07&longitude=72.87"
     "&hourly=pm2_5&forecast_days=1", None, {}),
]


def probe(pid, method, url, body, headers):
    rec = {"id": pid, "url": url[:110]}
    hdrs = {"User-Agent": "Aranya/0.1 (source probe)"}
    hdrs.update(headers or {})
    req = urllib.request.Request(url, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=TIMEOUT) as r:
            raw = r.read(2500).decode("utf8", "ignore")
            rec["status"] = r.status
            rec["ct"] = (r.headers.get("Content-Type") or "")[:40]
            rec["body"] = raw[:420].replace("\n", " ")
    except urllib.error.HTTPError as e:
        rec["status"] = e.code
    except Exception as e:
        rec["error"] = "%s: %s" % (type(e).__name__, str(e)[:120])
    return rec


for p in API_PROBES:
    print(json.dumps(probe(*p)))
