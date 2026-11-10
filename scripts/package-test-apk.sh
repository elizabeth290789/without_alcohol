#!/usr/bin/env bash
# Build 2.0; publish a signed update only if its signer matches shipped 1.0.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/cloud-env.sh
if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ "$1" != "--unsigned" ]; }; then
    echo 'Usage: bash scripts/package-test-apk.sh [--unsigned]' >&2
    exit 2
fi
./gradlew --console=plain assembleRelease
mkdir -p /workspace/artifacts
test_apk_tmp=$(mktemp /workspace/artifacts/day-by-day.XXXXXX.apk)
trap 'rm -f "$test_apk_tmp"' EXIT
cp app/build/outputs/apk/release/app-release-unsigned.apk "$test_apk_tmp"
mv "$test_apk_tmp" /workspace/artifacts/day-by-day-2.0-unsigned.apk
if [ "${1:-}" = "--unsigned" ]; then
    echo 'Unsigned build only; this file cannot update an installed application.'
    sha256sum /workspace/artifacts/day-by-day-2.0-unsigned.apk
    exit 0
fi
signing_keystore="${DAY_BY_DAY_KEYSTORE:-$ANDROID_USER_HOME/debug.keystore}"
if [ ! -f "$signing_keystore" ]; then
    echo 'The original 1.0 signing keystore is required. No signed 2.0 update was created.' >&2
    exit 1
fi
test_apk_tmp=$(mktemp /workspace/artifacts/day-by-day.XXXXXX.apk)
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
    --ks "$signing_keystore" \
    --ks-pass pass:android --key-pass pass:android \
    --v4-signing-enabled false \
    --out "$test_apk_tmp" \
    /workspace/artifacts/day-by-day-2.0-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$test_apk_tmp"
python3 - "$ANDROID_HOME/build-tools/35.0.0/apksigner" downloads/day-by-day-1.0.apk "$test_apk_tmp" <<'PY'
import re, subprocess, sys
def certificate(path):
    output = subprocess.check_output([sys.argv[1], 'verify', '--print-certs', path], text=True)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]+)', output)
    if not signers:
        raise SystemExit('Could not verify signing certificate; no update published.')
    return sorted(value.lower() for value in signers)
if certificate(sys.argv[2]) != certificate(sys.argv[3]):
    raise SystemExit('Signing key differs from 1.0. Android would reject the update; no signed 2.0 was published.')
PY
mv "$test_apk_tmp" /workspace/artifacts/day-by-day-2.0.apk
sha256sum /workspace/artifacts/day-by-day-2.0.apk
