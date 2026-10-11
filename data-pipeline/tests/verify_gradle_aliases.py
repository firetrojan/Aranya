"""Verify every `libs.<alias>` reference in build scripts exists in the version catalog.

A wrong catalog alias is an unresolved-reference error at script-compilation time, which
Gradle reports only after the wrapper runs. Since Gradle cannot run in this environment,
the check is done here: parse the aliases out of libs.versions.toml, then check that every
reference in build.gradle.kts maps to one of them.

Run: python3 data-pipeline/tests/verify_gradle_aliases.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CATALOG = os.path.join(ROOT, "android", "gradle", "libs.versions.toml")
GRADLE_FILES = [
    os.path.join(ROOT, "android", "build.gradle.kts"),
    os.path.join(ROOT, "android", "app", "build.gradle.kts"),
]


def catalog_aliases():
    text = open(CATALOG, encoding="utf-8").read()
    # Only entries in the [libraries] and [plugins] tables are addressable as libs.x / plugins.x.
    aliases = set()
    section = None
    for line in text.split("\n"):
        line = line.strip()
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if not line or line.startswith("#"):
            continue
        m = re.match(r"^([A-Za-z0-9_.-]+)\s*=\s*\{", line)
        if m and section in ("libraries", "plugins"):
            aliases.add(m.group(1))
    return aliases


def used_aliases(text):
    """
    Collect catalog references in the two forms the Kotlin DSL generates:

      libs.androidx.compose.ui.test.junit4     -> ("libs",  "androidx.compose.ui.test.junit4")
      alias(libs.plugins.android.application)   -> ("plugins", "android.application")
      libs.findPlugin("android.application")    -> ("plugins", "android.application")

    The `plugins.*` prefix is part of the accessor, not the alias, so it must be stripped.
    """
    used = set()
    for m in re.finditer(r"\blibs\.([A-Za-z0-9_.]+)", text):
        dotted = m.group(1)
        if dotted.startswith("plugins."):
            used.add(("plugins", dotted[len("plugins."):]))
        elif dotted.startswith("versions."):
            continue
        else:
            used.add(("libs", dotted))
    for m in re.finditer(r'\blibs\.findPlugin\("([^"]+)"\)', text):
        used.add(("plugins", m.group(1)))
    for m in re.finditer(r'\blibs\.findLibrary\("([^"]+)"\)', text):
        used.add(("libs", m.group(1)))
    return used


def camel_to_dotted(alias):
    """
    Map a Gradle catalog alias to the accessor Kotlin DSL generates.

    The catalog alias `androidx-compose-ui-test-junit4` is read as `libs.androidx.compose.ui.test.junit4`:
    dashes become dots, because that is how the version catalog exposes nested names.
    """
    return alias.replace("-", ".")


def main():
    aliases = catalog_aliases()
    if not aliases:
        print("FAIL: no aliases parsed from the version catalog")
        return 1
    print("catalog defines %d aliases" % len(aliases))

    problems = []
    checked = 0

    for path in GRADLE_FILES:
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, ROOT)
        for kind, dotted in sorted(used_aliases(text)):
            checked += 1
            # Accept both the dotted form and the raw alias form.
            if dotted in aliases or dotted.replace(".", "-") in aliases:
                continue
            problems.append("%s references %s.%s which is not in the catalog"
                            % (rel, kind, dotted))

    print("checked %d catalog references" % checked)
    print()
    if problems:
        print("FAILURES:")
        for p in problems:
            print("  - " + p)
        print()
        print("Catalog aliases available:")
        for a in sorted(aliases):
            print("  %s" % a)
        return 1
    print("PASS: every libs.* reference resolves to a catalog alias")
    return 0


if __name__ == "__main__":
    sys.exit(main())
