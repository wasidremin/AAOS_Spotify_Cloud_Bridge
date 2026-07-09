#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/setup-linux-env.sh"

AVD_NAME="${1:-CloudBridge_AAOS}"
emulator -avd "$AVD_NAME" -no-snapshot-load "$@"