#!/usr/bin/env bash
# Creates a compact APK signed with the public Android DEBUG key, for testing only.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/cloud-env.sh
./gradlew --console=plain assembleDebug assembleRelease
mkdir -p /workspace/artifacts
test_apk_tmp=$(mktemp /workspace/artifacts/day-by-day.XXXXXX.apk)
trap 'rm -f "$test_apk_tmp"' EXIT
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
    --ks "$ANDROID_USER_HOME/debug.keystore" \
    --ks-pass pass:android --key-pass pass:android \
    --v4-signing-enabled false \
    --out "$test_apk_tmp" \
    app/build/outputs/apk/release/app-release-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$test_apk_tmp"
mv "$test_apk_tmp" /workspace/artifacts/day-by-day-1.0.apk
sha256sum /workspace/artifacts/day-by-day-1.0.apk
