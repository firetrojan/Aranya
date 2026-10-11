"""Verify every pinned Gradle version actually exists in the repositories CI will use.

A wrong pin is not a compile error, it is a build that fails before it starts, and the
Stage 02 environment cannot run Gradle to find that out. This checks each pinned artifact
over plain HTTPS against the same repositories declared in settings.gradle.kts, so a bad
version is caught locally instead of in CI.

Run: python3 data-pipeline/tests/verify_gradle_versions.py
"""

import os
import re
import ssl
import sys
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CATALOG = os.path.join(ROOT, "android", "gradle", "libs.versions.toml")
CTX = ssl.create_default_context()
TIMEOUT = 25

# Repository bases matching settings.gradle.kts.
GOOGLE = "https://dl.google.com/dl/android/maven2/"
MAVEN = "https://repo1.maven.org/maven2/"
PLUGINS = "https://plugins.gradle.org/m2/"


def head(url):
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "Aranya/0.1"})
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=TIMEOUT) as r:
            return r.status == 200
    except urllib.error.HTTPError as e:
        return e.code == 200
    except Exception:
        return False


def artifact_url(base, group, name, version):
    return "%s%s/%s/%s/%s" % (
        base,
        group.replace(".", "/"),
        name,
        version,
        "%s-%s.pom" % (name, version),
    )


def plugin_marker_url(base, plugin_id, version):
    """
    A Gradle plugin id resolves through a *marker* artifact named after the full id.

    com.android.application  -> com.android.application.gradle.plugin
    com.google.devtools.ksp  -> com.google.devtools.ksp.gradle.plugin

    The marker is published under the id's own group, which is not simply the text before
    the final dot. Getting this wrong produces a false "not found".
    """
    return artifact_url(base, plugin_id, "%s.gradle.plugin" % plugin_id, version)


def main():
    text = open(CATALOG, encoding="utf-8").read()

    versions = dict(re.findall(r'^(\w+)\s*=\s*"([^"]+)"', text, re.M))
    libs = re.findall(
        r'^([\w-]+)\s*=\s*\{\s*module\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"(\w+)"', text, re.M
    )
    plugins = re.findall(
        r'^([\w-]+)\s*=\s*\{\s*id\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"(\w+)"', text, re.M
    )

    print("pinned versions: %s" % ", ".join("%s=%s" % kv for kv in sorted(versions.items())))
    print()

    missing = []
    checked = 0

    # Library modules live in Google or Maven Central.
    for alias, module, ref in libs:
        group, _, name = module.rpartition(":")
        version = versions.get(ref)
        if not version:
            missing.append("%s references unknown version key %r" % (alias, ref))
            continue
        checked += 1
        url = artifact_url(GOOGLE, group, name, version)
        if not head(url):
            url = artifact_url(MAVEN, group, name, version)
        ok = head(url)
        print("%-4s lib  %-46s %s" % ("OK" if ok else "MISS", alias, version))
        if not ok:
            missing.append("library %s (%s:%s) not found" % (alias, module, version))

    # Plugins live in the Gradle Plugin Portal, Google or Maven Central.
    for alias, plugin_id, ref in plugins:
        version = versions.get(ref)
        if not version:
            missing.append("%s references unknown version key %r" % (alias, ref))
            continue
        checked += 1
        ok = False
        for base in (PLUGINS, GOOGLE, MAVEN):
            if head(plugin_marker_url(base, plugin_id, version)):
                ok = True
                break
        print("%-4s plug %-46s %s" % ("OK" if ok else "MISS", plugin_id, version))
        if not ok:
            missing.append("plugin %s:%s not found" % (plugin_id, version))

    # The Compose compiler plugin only exists for Kotlin 2.x. This specific combination
    # was the Stage 02 build failure, so it is asserted explicitly rather than left to
    # the loop above.
    kotlin_major = versions.get("kotlin", "0").split(".")[0]
    checked += 1
    if kotlin_major == "1":
        print("FAIL Kotlin is %s but org.jetbrains.kotlin.plugin.compose requires 2.x"
              % versions.get("kotlin"))
        missing.append("kotlin %s cannot resolve the Compose compiler plugin" % versions.get("kotlin"))
    else:
        print("OK   kotlin %s is 2.x, Compose compiler plugin available" % versions.get("kotlin"))

    # KSP must match the Kotlin version exactly.
    checked += 1
    ksp = versions.get("ksp", "")
    if ksp and not ksp.startswith(versions.get("kotlin", "x")):
        print("FAIL ksp %s does not match kotlin %s" % (ksp, versions.get("kotlin")))
        missing.append("ksp %s does not match kotlin %s" % (ksp, versions.get("kotlin")))
    elif ksp:
        print("OK   ksp %s matches kotlin %s" % (ksp, versions.get("kotlin")))

    print()
    print("checked %d pinned versions" % checked)
    if missing:
        print("\nFAILURES:")
        for m in missing:
            print("  - " + m)
        return 1
    print("PASS: every pinned version resolves in a declared repository")
    return 0


if __name__ == "__main__":
    sys.exit(main())
