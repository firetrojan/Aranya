"""Check that documentation claims match verifiable reality.

A README that claims tests pass when they do not, or that cites a source status the
registry contradicts, is worse than no README. This checks the claims that can be
mechanically verified.

Run: python3 data-pipeline/tests/verify_docs_consistency.py
"""

import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DOCS = os.path.join(ROOT, "docs")


def read(path):
    p = path if os.path.isabs(path) else os.path.join(ROOT, path)
    if not os.path.exists(p):
        return ""
    return open(p, encoding="utf-8").read()


def main():
    failures = []
    checks = 0

    sources_yml = read("data/sources.yml")
    readme = read("README.md")
    arch = read("docs/ARCHITECTURE.md")
    testplan = read("docs/TEST_PLAN.md")
    datasources = read("docs/DATA_SOURCES.md")

    # 1. Registry status counts in the snapshot must match the docs.
    snap_path = os.path.join(ROOT, "android", "app", "src", "main", "assets", "sources_snapshot.json")
    snap = json.load(open(snap_path, encoding="utf-8"))
    counts = {}
    for r in snap:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    checks += 1
    verified_n = counts.get("verified", 0)
    if verified_n != 6:
        failures.append("expected 6 verified sources, snapshot has %d" % verified_n)

    # DATA_SOURCES.md states "Verified (6)".
    checks += 1
    if "## 1. Verified (6)" not in datasources:
        failures.append("DATA_SOURCES.md does not state the verified count that the registry has")

    # 2. README must NOT claim the Android build succeeded.
    checks += 1
    for phrase in ("build verified", "build passes", "APK built successfully"):
        if phrase.lower() in readme.lower():
            failures.append("README appears to claim a verified Android build: %r" % phrase)

    # 3. README and ARCHITECTURE must both disclose the Gradle blocker.
    checks += 2
    if "has not been verified" not in readme.lower() and "not been verified" not in readme.lower():
        failures.append("README does not disclose the unverified Android build")
    if "libnative-platform.so" not in arch:
        failures.append("ARCHITECTURE.md does not record the Gradle blocker evidence")

    # 4. Test plan totals must match the real suites.
    suites = {
        "test_sources_registry.py": 12,
        "test_normalize.py": 23,
        "test_schemas.py": 5,
        "verify_palette_contrast.py": 36,
        "verify_source_policy.py": 5,
    }
    total = sum(suites.values())
    for name, n in suites.items():
        checks += 1
        if "| `%s` | %d |" % (name, n) not in testplan:
            failures.append("TEST_PLAN.md count for %s does not match %d" % (name, n))
    checks += 1
    if ("86 checks" not in testplan) and ("%d checks" % total not in testplan):
        failures.append("TEST_PLAN.md total (%d expected) not stated" % total)

    # 5. Every source marked verified in the snapshot must have a verified_on date.
    checks += 1
    for r in snap:
        if r["status"] == "verified" and not r.get("verified_on"):
            failures.append("snapshot: %s is verified without verified_on" % r["id"])

    # 6. Key constraints must be documented, since they change the design.
    for needle, where, label in [
        ("non-commercial", datasources.lower(), "Open-Meteo non-commercial limit"),
        ("bulk", datasources.lower(), "OSM bulk download prohibition"),
        ("farm level", datasources.lower(), "SoilGrids farm-level warning"),
        ("dns", datasources.lower(), "DNS failures"),
    ]:
        checks += 1
        if needle not in where:
            failures.append("DATA_SOURCES.md does not mention: %s" % label)

    # 7. Docs must not promise Stage 01 features that do not exist.
    checks += 1
    for claim in ("crop recommendations are live", "mandi prices available",
                  "weather data is live", "works fully offline"):
        if claim.lower() in (readme + arch + datasources).lower():
            failures.append("docs overclaim: %r" % claim)

    # 8. Required docs all exist.
    for name in ("ARCHITECTURE.md", "DATA_SOURCES.md", "LICENCES.md", "AI_SAFETY.md",
                 "ROADMAP.md", "TEST_PLAN.md", "PRIVACY.md"):
        checks += 1
        if not os.path.exists(os.path.join(DOCS, name)):
            failures.append("missing docs/%s" % name)

    # 9. crop_knowledge / crop_rules must NOT exist yet (Stage 01 decision).
    for name in ("crop_knowledge", "crop_rules"):
        checks += 1
        if os.path.exists(os.path.join(ROOT, "data", name)):
            failures.append("data/%s should not exist in Stage 01" % name)

    print("checked %d documentation claims" % checks)
    if failures:
        print("\nFAILURES:")
        for f in failures:
            print("  - " + f)
        return 1
    print("PASS: documentation is consistent with verified reality")
    return 0


if __name__ == "__main__":
    sys.exit(main())
