#!/usr/bin/env bash
# Run every test that does not need an Android device.
#
# Portable: uses whatever `python3` is on PATH, so it works on CI, on a developer
# machine, and on a Linux environment. Do not hardcode an interpreter path here; an
# earlier version did and failed on GitHub Actions.
#
# Usage: bash scripts/run_tests.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 not found on PATH. Install Python 3.9 or newer." >&2
  exit 1
fi

FAILED=0
run() {
  echo ""
  echo "=== $1 ==="
  if python3 "$2"; then
    echo "--- OK: $1"
  else
    echo "--- FAILED: $1"
    FAILED=1
  fi
}

run "source registry"      data-pipeline/tests/test_sources_registry.py
run "normalization"        data-pipeline/tests/test_normalize.py
run "schemas"              data-pipeline/tests/test_schemas.py
run "palette contrast"     data-pipeline/tests/verify_palette_contrast.py
run "source policy"        data-pipeline/tests/verify_source_policy.py
run "android sources"      data-pipeline/tests/verify_android_sources.py
run "kotlin integrity"     data-pipeline/tests/verify_kotlin_integrity.py
run "icons in core"        data-pipeline/tests/verify_icons.py
run "docs consistency"     data-pipeline/tests/verify_docs_consistency.py
run "gradle versions"      data-pipeline/tests/verify_gradle_versions.py

echo ""
echo "=== registry snapshot is current ==="
if python3 data-pipeline/sources/build_snapshot.py; then
  echo "--- OK: snapshot"
else
  echo "--- FAILED: snapshot"
  FAILED=1
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "ALL RUNNABLE TESTS PASSED"
else
  echo "SOME TESTS FAILED"
fi
exit $FAILED
