"""Static consistency checks on the Android sources that do not require a compiler.

Gradle cannot run in this environment (docs/ARCHITECTURE.md documents the blocker), so the
Kotlin sources cannot be compiled. These checks catch the classes of error that would
otherwise only surface at build time: string resources referenced from Kotlin but not
defined, imports of the project's own classes that do not exist, and unbalanced braces.

This is NOT a substitute for compiling. It is a smoke test to keep obvious mistakes from
being committed blind.

Run: python3 data-pipeline/tests/verify_android_sources.py
"""

import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
KT = os.path.join(ROOT, "android", "app", "src")
MAIN_JAVA = os.path.join(KT, "main", "java")
RES_VALUES = os.path.join(KT, "main", "res", "values")


def kotlin_files():
    for dirpath, _d, filenames in os.walk(KT):
        for name in filenames:
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def defined_strings():
    """Names defined across every values*/strings.xml."""
    names = set()
    res_dir = os.path.join(KT, "main", "res")
    if not os.path.isdir(res_dir):
        return names
    for d in os.listdir(res_dir):
        if not d.startswith("values"):
            continue
        path = os.path.join(res_dir, d, "strings.xml")
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        names |= set(re.findall(r'<(?:string|plurals)\s+name="([^"]+)"', text))
    return names


def defined_plurals():
    names = set()
    res_dir = os.path.join(KT, "main", "res")
    for d in os.listdir(res_dir):
        path = os.path.join(res_dir, d, "strings.xml")
        if os.path.exists(path):
            text = open(path, encoding="utf-8").read()
            names |= set(re.findall(r'<plurals\s+name="([^"]+)"', text))
    return names


def main():
    failures = []
    checked = 0
    strings = defined_strings()
    plurals = defined_plurals()

    # 1. Every R.string.* / R.plurals.* referenced from Kotlin must exist.
    missing = set()
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        for ref in re.findall(r"\bR\.string\.(\w+)", text):
            checked += 1
            if ref not in strings:
                missing.add("%s -> R.string.%s" % (os.path.relpath(path, ROOT), ref))
        for ref in re.findall(r"\bR\.plurals\.(\w+)", text):
            checked += 1
            if ref not in plurals:
                missing.add("%s -> R.plurals.%s" % (os.path.relpath(path, ROOT), ref))
    if missing:
        failures.append("undefined string resources:\n      " + "\n      ".join(sorted(missing)))

    # 2. Own-package imports must resolve to a real file.
    own = {}
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        m = re.search(r"^package\s+([\w.]+)", text, re.M)
        if m:
            own[m.group(1)] = path
    bad_imports = []
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        for imp in re.findall(r"^import\s+(org\.aranya\.app\.[\w.]+)", text, re.M):
            checked += 1
            pkg = imp.rsplit(".", 1)[0]
            if pkg not in own:
                bad_imports.append("%s -> %s" % (os.path.relpath(path, ROOT), imp))
    if bad_imports:
        failures.append("imports of packages that do not exist:\n      " + "\n      ".join(sorted(bad_imports)))

    # 3. Brace balance, a cheap but effective truncation check.
    unbalanced = []
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        # Strip strings and comments before counting.
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text)
        stripped = re.sub(r"//[^\n]*", "", stripped)
        stripped = re.sub(r"/\*.*?\*/", "", stripped, flags=re.S)
        checked += 1
        if stripped.count("{") != stripped.count("}"):
            unbalanced.append(os.path.relpath(path, ROOT))
        if stripped.count("(") != stripped.count(")"):
            unbalanced.append(os.path.relpath(path, ROOT) + " (parens)")
    if unbalanced:
        failures.append("unbalanced braces/parens in:\n      " + "\n      ".join(sorted(set(unbalanced))))

    # 4. The asset the app loads must exist and be valid JSON.
    asset = os.path.join(KT, "main", "assets", "sources_snapshot.json")
    checked += 1
    if not os.path.exists(asset):
        failures.append("missing asset sources_snapshot.json referenced by SourceRegistry")
    else:
        try:
            data = json.load(open(asset, encoding="utf-8"))
            if not isinstance(data, list) or not data:
                failures.append("sources_snapshot.json is not a non-empty list")
        except Exception as exc:
            failures.append("sources_snapshot.json is not valid JSON: %s" % exc)

    # 5b. Every resource declared in a qualified values-* folder must also exist in the
    #     base values folder. A qualified-only resource cannot be resolved when the
    #     configuration does not match, which crashes the launch theme at startup.
    #     (Lint issue MissingDefaultResource; this was a real Stage 02 CI failure.)
    base_values = {}
    qual_values = {}
    res_main = os.path.join(KT, "main", "res")
    for d in sorted(os.listdir(res_main)):
        if not d.startswith("values"):
            continue
        path = os.path.join(res_main, d, "strings.xml")
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        if d == "values":
            base_values.update(
                (m.group(1), m.group(2)) for m in re.finditer(
                    r'<(string|plurals|color)\s+name="([^"]+)"', text))
        else:
            qual_values.setdefault(d, {}).update(
                (m.group(1), m.group(2)) for m in re.finditer(
                    r'<(string|plurals|color)\s+name="([^"]+)"', text))

    colors_base = set()
    colors_qual = {}
    for d in sorted(os.listdir(res_main)):
        if not d.startswith("values"):
            continue
        path = os.path.join(res_main, d, "colors.xml")
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        names = set(re.findall(r'<color\s+name="([^"]+)"', text))
        if d == "values":
            colors_base |= names
        else:
            colors_qual.setdefault(d, set()).update(names)

    for d, names in colors_qual.items():
        missing_colors = names - colors_base
        checked += len(names)
        if missing_colors:
            failures.append(
                "res/%s/colors.xml declares %s with no base values/colors.xml entry; "
                "Lint MissingDefaultResource, and it can crash the launch theme"
                % (d, ", ".join(sorted(missing_colors))))

    # 6. Build config sanity: minSdk/targetSdk present and coherent.
    checked += 1
    gradle = os.path.join(ROOT, "android", "app", "build.gradle.kts")
    gtext = open(gradle, encoding="utf-8").read() if os.path.exists(gradle) else ""
    for key in ("minSdk", "targetSdk", "compileSdk", "namespace"):
        if key not in gtext:
            failures.append("build.gradle.kts does not set %s" % key)
    if "resourceConfigurations" not in gtext:
        failures.append("build.gradle.kts does not pin resourceConfigurations")

    print("checked %d assertions across %d Kotlin files" % (checked, len(list(kotlin_files()))))
    if failures:
        print("\nFAILURES:")
        for f in failures:
            print("  - " + f)
        return 1
    print("PASS: all Android source consistency checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
