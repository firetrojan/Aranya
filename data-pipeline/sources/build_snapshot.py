"""Generate the on-device source registry snapshot from data/sources.yml.

The YAML registry is the authoritative record. The app cannot parse YAML without a
dependency, and shipping a YAML parser for a small read-only list would add size for no
benefit. Instead the registry is projected once, at build time, into JSON that the app
reads from its assets.

The projection carries only what the Source Status screen needs. Licence details and
limitations are included because the screen is required to show them; API keys and other
secrets never appear in sources.yml and must never be added to it.

Run: python3 data-pipeline/sources/build_snapshot.py
"""

from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import registry  # noqa: E402

REGISTRY_PATH = os.path.join(ROOT, "data", "sources.yml")
OUTPUT_PATH = os.path.join(ROOT, "android", "app", "src", "main", "assets", "sources_snapshot.json")

# Order the screen shows sources in: most trustworthy and most useful first, so the user
# sees what actually works before what does not.
STATUS_ORDER = ["verified", "restricted", "investigating", "unavailable", "not_integrated"]


def build() -> int:
    entries = registry.load_registry(REGISTRY_PATH)
    if not entries:
        print("FAIL: registry is empty")
        return 1

    records = []
    for entry in entries.values():
        records.append({
            "id": entry.id,
            "name": entry.name,
            "official_url": entry.entry.get("official_url"),
            "category": entry.category,
            "geography": entry.entry.get("geography"),
            "licence": entry.licence,
            "attribution_required": entry.attribution_required,
            "attribution_text": entry.entry.get("attribution_text"),
            "status": entry.status,
            "last_success": entry.last_success,
            "verified_on": entry.verified_on,
            "limitations": entry.entry.get("limitations"),
            "fallback": entry.entry.get("fallback"),
        })

    records.sort(key=lambda r: (STATUS_ORDER.index(r["status"]), r["id"]))

    # Refuse to emit anything that would make the app overstate what works.
    for r in records:
        if r["status"] == "verified" and not r["verified_on"]:
            print("FAIL: %s is verified with no verified_on date" % r["id"])
            return 1

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as fh:
        json.dump(records, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    counts = {}
    for r in records:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    print("Wrote %d sources to %s" % (len(records), os.path.relpath(OUTPUT_PATH, ROOT)))
    for status in STATUS_ORDER:
        if status in counts:
            print("  %-16s %d" % (status, counts[status]))
    return 0


if __name__ == "__main__":
    sys.exit(build())
