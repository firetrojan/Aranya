"""Distinguish real DNS/API failures from harness-local network limits.

Checks whether a hostname resolves at all, and compares against a control host
known to work, so registry statuses record a true cause rather than a guess.
"""
import json
import socket
import ssl
import urllib.error
import urllib.request

CTX = ssl.create_default_context()
HOSTS = [
    "api.imd.gov.in",
    "mausam.imd.gov.in",
    "imd.gov.in",
    "agmarknet.gov.in",
    "data.gov.in",
    "api.data.gov.in",
    "des.gov.in",
    "www.des.gov.in",
    "moagri.gov.in",
    "www.moagri.gov.in",
    "agricoop.nic.in",
    "www.agricoop.nic.in",
    "agmarknet.gov.in",
    "example.com",
]


def resolve(host):
    try:
        socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
        return {"dns": "resolves"}
    except Exception as e:
        return {"dns": "FAIL: %s" % type(e).__name__}


def head(host):
    url = "https://%s/" % host
    req = urllib.request.Request(url, headers={"User-Agent": "Aranya/0.1 (source probe)"})
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=15) as r:
            return {"http": r.status, "ct": (r.headers.get("Content-Type") or "")[:40]}
    except urllib.error.HTTPError as e:
        return {"http": e.code}
    except Exception as e:
        return {"http": "FAIL: %s: %s" % (type(e).__name__, str(e)[:80])}


out = []
for h in HOSTS:
    rec = {"host": h}
    rec.update(resolve(h))
    rec.update(head(h))
    out.append(rec)

for r in out:
    print(json.dumps(r))
