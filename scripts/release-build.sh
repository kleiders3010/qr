#!/usr/bin/env bash
# Builds, tests and signs QR's release APK + Play bundle into dist/, then runs the launch test on the running emulator.
# Usage: bash scripts/release-build.sh <version>
set -e
V="${1:?version}"
Q=C:/AI/Projects/Programs/QR
cd "$Q/app"
python C:/Users/uri_l/MachineQueue/heavy.py "QR: gradle assembleRelease" -- "$Q/app/gradlew.bat" -q -p "$Q/app" :android:testDebugUnitTest :android:assembleRelease :android:bundleRelease
mkdir -p "$Q/dist"
powershell -File "$Q/scripts/sign-release.ps1" "$Q/app/android/build/outputs/apk/release/android-release-unsigned.apk" "$Q/dist/QR-$V.apk"
powershell -File "$Q/scripts/sign-bundle.ps1" "$Q/app/android/build/outputs/bundle/release/android-release.aab" "$Q/dist/qr-$V-play.aab"
python C:/AI/Projects/Programs/SelfSustainment/tools/android_launch_test.py "$Q/dist/QR-$V.apk" com.jurisgames.qr
sha256sum "$Q/dist/QR-$V.apk"
