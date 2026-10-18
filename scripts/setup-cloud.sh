#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/cloud-env.sh
mkdir -p /workspace/tools "$ANDROID_HOME/cmdline-tools" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"

if [ ! -x '/workspace/tools/jdk-17.0.20.1+1/bin/javac' ]; then
    curl --fail --location --retry 2 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' -o /workspace/tools/jdk17.tar.gz
    python3 - <<'PY'
import hashlib, tarfile
p = '/workspace/tools/jdk17.tar.gz'
assert hashlib.sha256(open(p, 'rb').read()).hexdigest() == '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e', 'JDK checksum mismatch'
with tarfile.open(p) as archive:
    archive.extractall('/workspace/tools', filter='data')
PY
fi
export JAVA_HOME='/workspace/tools/jdk-17.0.20.1+1'
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl --fail --location --retry 2 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' -o /workspace/tools/commandlinetools.zip
    python3 - <<'PY'
import hashlib, os, zipfile
p = '/workspace/tools/commandlinetools.zip'
# Checksum published in Google's signed-TLS repository manifest for tools 19.0.
assert hashlib.sha1(open(p, 'rb').read()).hexdigest() == '5fdcc763663eefb86a5b8879697aa6088b041e70', 'Android tools checksum mismatch'
root = os.environ['ANDROID_HOME'] + '/cmdline-tools'
with zipfile.ZipFile(p) as archive:
    archive.extractall(root)
os.rename(root + '/cmdline-tools', root + '/latest')
PY
    chmod +x "$ANDROID_HOME/cmdline-tools/latest/bin/"*
fi

# Java does not read HTTPS_PROXY automatically. Keep machine-specific settings
# in the Gradle user directory, never in the project or saved credentials.
python3 - <<'PY'
import os, pathlib, urllib.parse
p = pathlib.Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
existing = p.read_text() if p.exists() else ''
settings = {}
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    for scheme in ('http', 'https'):
        settings[f'systemProp.{scheme}.proxyHost'] = proxy.hostname
        settings[f'systemProp.{scheme}.proxyPort'] = str(proxy.port or 80)
    settings['systemProp.http.nonProxyHosts'] = 'localhost|127.*|[::1]'
trust = pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust.is_file():
    settings['systemProp.javax.net.ssl.trustStore'] = str(trust)
known = {line.split('=', 1)[0] for line in existing.splitlines() if '=' in line}
extra = [f'{key}={value}' for key, value in settings.items() if key not in known]
if extra:
    p.write_text(existing.rstrip() + '\n' + '\n'.join(extra) + '\n')
PY

python3 - <<'PY'
import os, subprocess, urllib.parse
args = [os.environ['ANDROID_HOME'] + '/cmdline-tools/latest/bin/sdkmanager', '--sdk_root=' + os.environ['ANDROID_HOME']]
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    args += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port or 80)]
subprocess.run(args + ['--licenses'], input='y\n' * 100, text=True, check=True)
packages = ['platforms;android-35', 'build-tools;35.0.0', 'platform-tools']
if os.environ.get('WITH_EMULATOR') == '1':
    packages += ['emulator', 'system-images;android-28;default;x86_64']
subprocess.run(args + packages, check=True)
PY
./gradlew --console=plain assembleDebug assembleRelease testDebugUnitTest lintDebug assembleDebugAndroidTest
