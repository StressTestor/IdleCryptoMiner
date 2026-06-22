#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_ICON="${1:-$ROOT_DIR/store-assets/ic_launcher-512.png}"
DEST_DIR="$ROOT_DIR/ios/IdleCryptoMiner/Assets.xcassets/AppIcon.appiconset"

if ! command -v sips >/dev/null 2>&1; then
  echo "sips is required. Run this on macOS." >&2
  exit 1
fi

if [[ ! -f "$SOURCE_ICON" ]]; then
  echo "source icon not found: $SOURCE_ICON" >&2
  echo "pass a square PNG path, or add store-assets/ic_launcher-512.png" >&2
  exit 1
fi

mkdir -p "$DEST_DIR"

make_icon() {
  local pixels="$1"
  local name="$2"
  sips -s format png -z "$pixels" "$pixels" "$SOURCE_ICON" --out "$DEST_DIR/$name" >/dev/null
}

make_icon 40 Icon-20@2x.png
make_icon 60 Icon-20@3x.png
make_icon 58 Icon-29@2x.png
make_icon 87 Icon-29@3x.png
make_icon 80 Icon-40@2x.png
make_icon 120 Icon-40@3x.png
make_icon 120 Icon-60@2x.png
make_icon 180 Icon-60@3x.png
make_icon 1024 Icon-1024.png

echo "generated iOS app icons in $DEST_DIR"
