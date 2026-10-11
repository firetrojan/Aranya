#!/system/bin/sh
# Bootstrap instructions for the Gradle wrapper.
#
# The wrapper JAR is a binary and is not committed to this repository. Regenerate it once
# on a machine with a working JDK:
#
#   cd android
#   gradle wrapper --gradle-version 8.7
#
# That writes android/gradle/wrapper/gradle-wrapper.jar and the gradlew / gradlew.bat
# scripts. Commit them afterwards so every developer and CI run uses the same Gradle.
#
# Committing the JAR was considered and rejected: it is a ~60 KB opaque binary that
# changes on every Gradle upgrade, and `gradle wrapper` regenerates it deterministically.
#
# Until the wrapper JAR exists, the build can still be run with a local Gradle 8.7+:
#
#   cd android && gradle :app:assembleDebug
#
# See docs/ARCHITECTURE.md for the full build requirements.
echo "Run: cd android && gradle wrapper --gradle-version 8.7"
