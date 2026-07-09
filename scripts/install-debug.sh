#!/usr/bin/env bash
# Install/upgrade the debug APK WITHOUT uninstalling.
# Uninstall wipes OAuth profiles — never do that as part of a normal deploy.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/setup-linux-env.sh"

cd "$REPO_ROOT"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if [[ ! -f "$APK" ]]; then
  echo "Building debug APK..."
  ./gradlew assembleDebug --console=plain
fi

if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK not found at $APK"
  exit 1
fi

if ! adb devices | grep -qE 'device$'; then
  echo "ERROR: no adb device online"
  exit 1
fi

echo "Installing (replace in place, keep app data): $APK"
set +e
OUT=$(adb install -r "$APK" 2>&1)
STATUS=$?
set -e
echo "$OUT"

if [[ $STATUS -ne 0 ]]; then
  if echo "$OUT" | grep -qi 'UPDATE_INCOMPATIBLE'; then
    cat <<'EOF'

ERROR: Signature mismatch — Android refused the upgrade.
This used to be "fixed" by uninstalling, which WIPES Spotify tokens.

Do NOT run: adb uninstall com.cloudbridge.spotify

Instead:
  1. Rebuild with the same signingConfig (app/build.gradle.kts "local")
  2. Confirm debug/release both use signingConfigs.local
  3. Retry: scripts/install-debug.sh

If you truly need a clean install, export a profile via QR refresh first.
EOF
  fi
  exit "$STATUS"
fi

echo "Launching..."
adb shell am start -n com.cloudbridge.spotify/.ui.MainActivity
echo "Done — app data (tokens/profiles) preserved."
