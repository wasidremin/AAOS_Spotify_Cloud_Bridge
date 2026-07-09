#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/setup-linux-env.sh"

cd "$REPO_ROOT"
./gradlew testDebugUnitTest assembleDebug --console=plain "$@"

# Optional: install without wiping tokens (set INSTALL=1)
if [[ "${INSTALL:-0}" == "1" ]]; then
  "$SCRIPT_DIR/install-debug.sh"
fi