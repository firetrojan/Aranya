"""Distinguish a SoilGrids parameter error from genuine no-data at a point.

`properties/query` returned `{"layers":[{... "values":{"mean":null}}]}` for Pune, while
`classification/query` returned a real soil class for the same coordinate. This probe
checks whether numeric properties ever return a value here, so the registry can say
whether soil values are usable or the endpoint is effectively unusable for them.
"""
import json
import ssl
import urllib.request

CTX = ssl.create_default_context()

# (label, url)
CASES = [
    ("phh2o_0-5cm_mean", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=72.87&lat=19.07&property=phh2o&depth=0-5cm&value=mean&units=ph"),
    ("phh2o_0-20cm_mean", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=72.87&lat=19.07&property=phh2o&depth=0-20cm&value=mean&units=ph"),
    ("clay_0-20cm_mean", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=72.87&lat=19.07&property=clay&depth=0-20cm&value=mean"),
    ("silt_0-20cm_mean", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=72.87&lat=19.07&property=silt&depth=0-20cm&value=mean"),
    ("phh2o_28.61_8.74_27.12_9.15", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=77.2&lat=28.6&property=phh2o&depth=0-5cm&value=mean&units=ph"),
    ("clay_delhi", "https://rest.isric.org/soilgrids/v2.0/properties/query"
     "?lon=77.2&lat=28.6&property=clay&depth=0-20cm&value=mean"),
    ("class_delhi", "https://rest.isric.org/soilgrids/v2.0/classification/query?lon=77.2&lat=28.6"),
]


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Aranya/0.1 (source probe)"})
    with urllib.request.urlopen(req, context=CTX, timeout=30) as r:
        return r.status, r.read(4000).decode("utf8", "ignore")


for label, url in CASES:
    try:
        status, body = get(url)
        try:
            parsed = json.loads(body)
            layers = parsed.get("properties", {}).get("layers")
            summary = "classification=%s" % parsed.get("wrb_class_name")
            if layers is None:
                summary += " (no layers key)"
            else:
                vals = [(d.get("label"), d.get("values")) for d in (layers[0].get("depths") or [])] if layers else []
                summary += " values=%s" % vals
            print("%-34s %s %s" % (label, status, summary))
        except Exception:
            print("%-34s %s unparsed: %s" % (label, status, body[:120]))
    except Exception as e:
        print("%-34s ERR %s: %s" % (label, type(e).__name__, str(e)[:90]))
