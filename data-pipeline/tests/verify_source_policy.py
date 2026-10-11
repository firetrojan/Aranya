"""Replicate the SourcePolicyTest rules outside JUnit, so they can actually be run here.

Gradle cannot run in this environment (see docs/ARCHITECTURE.md), so the Kotlin
host-side tests are unverifiable locally. This script applies the SAME rules with the
same regular expressions so the policy is enforced now, not merely asserted.

Run: python3 data-pipeline/tests/verify_source_policy.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
KT_DIR = os.path.join(ROOT, "android", "app", "src", "main", "java")
RES_DIR = os.path.join(ROOT, "android", "app", "src", "main", "res")
GRADLE = os.path.join(ROOT, "android", "app", "build.gradle.kts")

QUOTED = re.compile(r'"([^"\\]|\\.){4,}"')
SECRET = re.compile(
    r"""(api[_-]?key|apikey|secret|token|client[_-]?secret)\s*[:=]\s*['"][A-Za-z0-9_\-]{16,}['"]""",
    re.IGNORECASE,
)


def kotlin_files():
    for dirpath, _dirnames, filenames in os.walk(KT_DIR):
        for name in filenames:
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def check_no_hardcoded_strings():
    offenders = []
    for path in kotlin_files():
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding="utf-8") as fh:
            for i, line in enumerate(fh.read().split("\n"), start=1):
                trimmed = line.strip()
                if trimmed.startswith("*") or trimmed.startswith("//") or trimmed.startswith("/*"):
                    continue
                if not QUOTED.search(trimmed):
                    continue
                is_ui_call = (
                    "Text(" in trimmed
                    or "contentDescription =" in trimmed
                    or "title =" in trimmed
                )
                if is_ui_call:
                    offenders.append("%s:%d: %s" % (rel, i, trimmed))
    return offenders


def check_no_dynamic_colour():
    return [
        os.path.relpath(p, ROOT)
        for p in kotlin_files()
        if "dynamicLightColorScheme" in open(p, encoding="utf-8").read()
        or "dynamicDarkColorScheme" in open(p, encoding="utf-8").read()
    ]


def check_no_analytics():
    if not os.path.exists(GRADLE):
        return []
    text = open(GRADLE, encoding="utf-8").read().lower()
    banned = [
        "firebase", "crashlytics", "admob", "appsflyer", "adjust", "branch.io",
        "amplitude", "mixpanel", "segment", "facebook", "google-services",
        "play-services-location", "play-services-maps", "mapbox",
    ]
    return [t for t in banned if t in text]


def check_no_secrets():
    offenders = []
    for path in kotlin_files():
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding="utf-8") as fh:
            for i, line in enumerate(fh.read().split("\n"), start=1):
                trimmed = line.strip()
                if trimmed.startswith("*") or trimmed.startswith("//"):
                    continue
                if SECRET.search(line):
                    offenders.append("%s:%d: %s" % (rel, i, trimmed[:80]))
    return offenders


def check_single_locale():
    """Flag extra *locale* resource dirs only.

    `values-night` is a configuration qualifier (dark theme), not a locale, and Aranya
    legitimately ships it. A real extra language would appear as values-hi, values-mr etc.
    """
    offenders = []
    if not os.path.isdir(RES_DIR):
        return offenders
    # Qualifiers that change configuration rather than language.
    non_locale = {"values-night", "values-day", "values-v26", "values-v31"}
    for name in sorted(os.listdir(RES_DIR)):
        if not name.startswith("values"):
            continue
        if name == "values" or name in non_locale:
            continue
        offenders.append(name)
    return offenders


def main():
    checks = [
        ("no hardcoded user-facing strings", check_no_hardcoded_strings),
        ("no Material You dynamic colour", check_no_dynamic_colour),
        ("no analytics/ads/commercial maps", check_no_analytics),
        ("no committed API keys or secrets", check_no_secrets),
        ("English-only resource directories", check_single_locale),
    ]

    failed = 0
    total = 0
    for label, fn in checks:
        offenders = fn()
        total += 1
        if offenders:
            failed += 1
            print("FAIL %s" % label)
            for o in offenders[:10]:
                print("       %s" % o)
            if len(offenders) > 10:
                print("       ... and %d more" % (len(offenders) - 10))
        else:
            print("PASS %s" % label)

    print("\n%d/%d checks passed" % (total - failed, total))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
