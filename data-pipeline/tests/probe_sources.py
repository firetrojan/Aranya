"""Probe candidate Aranya data sources for real, current accessibility.

No source is marked verified in the registry unless this probe actually reaches it
and returns a usable response. Run:  python3 data-pipeline/tests/probe_sources.py
"""
import json
import ssl
import urllib.error
import urllib.request

CTX = ssl.create_default_context()
TIMEOUT = 20

# (id, url, expected content-type fragment, extra headers)
PROBES = [
    ("imd_weather_api", "https://api.imd.gov.in/", None, {}),
    ("open_meteo_forecast", "https://api.open-meteo.com/v1/forecast?latitude=19.07&longitude=72.87&daily=temperature_2m_max&timezone=Asia%2FKolkata", "json", {}),
    ("nasa_power", "https://power.larc.nasa.gov/api/temporal/daily/point?parameters=T2M&community=AG&longitude=72.87&latitude=19.07&start=20240101&end=20240105&format=JSON", "json", {}),
    ("soilgrids_wcs", "https://maps.isric.org/mapsserv?service=WCS&request=GetCapabilities", None, {}),
    ("soilgrids_rest", "https://rest.isric.org/soilgrids/v2.0/properties/query?lon=72.87&lat=19.07&property=phh2o&depth=0-5&value=mean", "json", {}),
    ("data_gov_in", "https://api.data.gov.in/", None, {}),
    ("agmarknet", "https://agmarknet.gov.in/SearchCmmMkt.aspx", None, {}),
    ("des_statistics", "https://www.des.gov.in/", None, {}),
    ("moagri_statistics", "https://www.moagri.gov.in/", None, {}),
    ("copernicus_data_space", "https://catalogue.dataspace.copernicus.eu/", None, {}),
    ("isro_bhoonidhi", "https://bhoonidhi.nrsc.gov.in/", None, {}),
    ("bhuvan", "https://bhuvan.nrsc.gov.in/", None, {}),
    ("dgft", "https://www.dgft.gov.in/", None, {}),
    ("apeda", "https://www.apeda.gov.in/", None, {}),
    ("commerce_mib", "https://www.commerce.gov.in/", None, {}),
    ("agricoop", "https://www.agricoop.nic.in/", None, {}),
    ("osm_overpass", "https://overpass-api.de/api/status", "json", {}),
    ("osm_nominatim", "https://nominatim.openstreetmap.org/status.php", None, {"User-Agent": "Aranya/0.1 (research probe)"}),
    ("fao_gsrs", "https://www.fao.org/", None, {}),
]


def probe(name, url, want_ct, headers):
    req = urllib.request.Request(url, headers=headers or {"User-Agent": "Aranya/0.1 (source probe)"})
    rec = {"id": name, "url": url}
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=TIMEOUT) as r:
            body = r.read(400)
            rec["status"] = r.status
            rec["content_type"] = r.headers.get("Content-Type", "")
            if want_ct:
                rec["ok"] = want_ct in rec["content_type"]
                rec["sample"] = body.decode("utf8", "ignore")[:160].replace("\n", " ")
            else:
                rec["ok"] = True
                rec["sample"] = body.decode("utf8", "ignore")[:80].replace("\n", " ")
    except urllib.error.HTTPError as e:
        rec["status"] = e.code
        rec["ok"] = False
        rec["error"] = "HTTPError %s" % e.code
    except Exception as e:
        rec["ok"] = False
        rec["error"] = "%s: %s" % (type(e).__name__, str(e)[:120])
    return rec


def main():
    results = [probe(*p) for p in PROBES]
    print(json.dumps(results, indent=2))
    ok = sum(1 for r in results if r.get("ok"))
    print("\nreachable_and_usable=%d/%d" % (ok, len(results)))


if __name__ == "__main__":
    main()
