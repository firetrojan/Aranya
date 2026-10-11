"""Probe real API behaviour (auth, params, response shape) for Aranya candidate sources.

Records what each endpoint actually requires, so the registry can state auth and
access requirements as verified fact rather than assumption.
"""
import json
import ssl
import urllib.error
import urllib.request

CTX = ssl.create_default_context()
TIMEOUT = 25

# id, method, url, body, headers
API_PROBES = [
    ("imd_api_noauth", "GET",
     "https://api.imd.gov.in/weather?lat=19.07&lon=72.87", None, {}),
    ("imd_api_openend", "GET",
     "https://api.imd.gov.in/weather?lat=19.07&lon=72.87&end=20261010", None, {}),
    ("open_meteo_full", "GET",
     "https://api.open-meteo.com/v1/forecast?latitude=19.07&longitude=72.87"
     "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max"
     "&current=temperature_2m,relative_humidity_2m&forecast_days=3&timezone=Asia%2FKolkata",
     None, {}),
    ("open_meteo_archive", "GET",
     "https://archive-api.open-meteo.com/v1/archive?latitude=19.07&longitude=72.87"
     "&start_date=2024-01-01&end_date=2024-01-10&daily=temperature_2m_mean&timezone=Asia%2FKolkata",
     None, {}),
    ("nasa_power_point", "GET",
     "https://power.larc.nasa.gov/api/temporal/daily/point?parameters=T2M,PRECTOTCORR"
     "&community=AG&longitude=72.87&latitude=19.07&start=20240101&end=20240110&format=JSON",
     None, {}),
    ("soilgrids_rest", "GET",
     "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=72.87&lat=19.07"
     "&property=phh2o&depth=0-5&value=mean&units=ph", None, {}),
    ("soilgrids_classification", "GET",
     "https://rest.isric.org/soilgrids/v2.0/classification/query?lon=72.87&lat=19.07", None, {}),
    ("datagov_resource_catalog", "GET",
     "https://api.data.gov.in/catalog/4408bd8c-0d0f-4e0f-8f28-ff9c1e1d3a1f?format=json&limit=2&api-key=579b464db66ec23bdd000001cbf7c7b73bd3c6f4b1b0c4f4c0f0c0f0c0f0c0f0",
     None, {}),
    ("datagov_root_catalog", "GET",
     "https://api.data.gov.in/catalog?format=json&limit=1", None, {}),
    ("copernicus_odata", "GET",
     "https://catalogue.dataspace.copernicus.eu/odata/v1/Products?$top=1", None, {}),
    ("copernicus_stac", "GET",
     "https://catalogue.dataspace.copernicus.eu/stac/collections", None, {}),
    ("overpass_query", "POST",
     "https://overpass-api.de/api/interpreter",
     'data=[out:json][timeout:25];node(19.07,72.87,19.08,72.88)["amenity"="marketplace"];out 3;',
     {"Content-Type": "application/x-www-form-urlencoded"}),
    ("nominatim_search", "GET",
     "https://nominatim.openstreetmap.org/search?q=Pune&format=json&limit=1",
     None, {"User-Agent": "Aranya/0.1 (agricultural research; contact via repo)"}),
]


def probe(pid, method, url, body, headers):
    rec = {"id": pid, "method": method, "url": url[:120]}
    data = body.encode() if body else None
    hdrs = {"User-Agent": "Aranya/0.1 (source probe)"}
    hdrs.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=TIMEOUT) as r:
            raw = r.read(3000).decode("utf8", "ignore")
            rec["status"] = r.status
            rec["ct"] = (r.headers.get("Content-Type") or "")[:50]
            rec["body"] = raw[:400].replace("\n", " ")
    except urllib.error.HTTPError as e:
        rec["status"] = e.code
        try:
            rec["body"] = e.read(400).decode("utf8", "ignore").replace("\n", " ")
        except Exception:
            rec["body"] = ""
    except Exception as e:
        rec["error"] = "%s: %s" % (type(e).__name__, str(e)[:140])
    return rec


for p in API_PROBES:
    print(json.dumps(probe(*p)))
