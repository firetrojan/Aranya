#!/system/bin/sh
# Run every test that can execute in this environment.
#
# Gradle cannot run here (see docs/ARCHITECTURE.md), so the Kotlin unit and instrumented
# tests are unverified. This runs everything that does not need a device or Gradle.
#
# Usage: sh scripts/run_tests.sh
L=/data/user/0/com.androidharness.app/files/linux
# python3 here is an ELF binary that must be launched through the platform linker, so it
# cannot be invoked as `sh script.py`. Use the launcher that works on this device.
PYSH="$L/bin/bash -c"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1

FAILED=0
run() {
  echo ""
  echo "=== $1 ==="
  if "$L/bin/python3" "$2"; then
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

echo ""
echo "=== registry snapshot is current ==="
if "$L/bin/python3" data-pipeline/sources/build_snapshot.py; then
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
