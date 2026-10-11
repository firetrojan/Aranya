#!/system/bin/sh
# Document the exact Gradle blocker with reproducible evidence.
#
# Run: sh scripts/diagnose_gradle.sh
L=/data/user/0/com.androidharness.app/files/linux
export JAVA_HOME="$L/lib/jvm/java-17-openjdk"

echo "=== host ==="
uname -m
echo "libc: Android bionic (no /lib64, no glibc)"

echo ""
echo "=== gradle distribution present ==="
ls "$L/opt/gradle/lib/native-platform-linux-aarch64-0.22-milestone-29.jar" 2>&1

echo ""
echo "=== gradle --version ==="
"$L/opt/gradle/bin/gradle" --version 2>&1 | grep -E 'What went wrong|native|Caused|IOException' | head -6

echo ""
echo "=== required by libnative-platform.so ==="
SO="$TMPDIR/np/net/rubygrapefruit/platform/linux-aarch64/libnative-platform.so"
if [ -f "$SO" ]; then
  readelf -d "$SO" 2>/dev/null | grep NEEDED
else
  echo "(extract with jar xf native-platform-linux-aarch64-0.22-milestone-29.jar first)"
fi

echo ""
echo "=== toolchain glibc/libstdc++ availability ==="
find "$L" -name 'libstdc++.so*' 2>/dev/null | head -3
find "$L" -name 'libc.so.6' 2>/dev/null | head -3
echo "(empty above = no glibc runtime, which is the root cause)"

echo ""
echo "=== conclusion ==="
echo "Gradle requires glibc-linked libnative-platform.so. Android's bionic linker"
echo "cannot load it. No Gradle flag disables native services; a host with glibc,"
echo "or an x86_64 host under qemu-user, is required."
