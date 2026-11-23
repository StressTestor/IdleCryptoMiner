#!/usr/bin/env bash
set +x
set -euo pipefail

for name in APP_STORE_CONNECT_ISSUER_ID APP_STORE_CONNECT_KEY_ID APP_STORE_CONNECT_API_KEY_P8 APPLE_TEAM_ID; do
  if [[ -z "${!name:-}" ]]; then
    printf 'error: required environment variable %s is missing or empty\n' "$name" >&2
    exit 1
  fi
done

IOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_NUMBER="${BUILD_NUMBER:-$(date -u +%Y%m%d%H%M%S)}"
if [[ ! "$BUILD_NUMBER" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
  printf 'error: BUILD_NUMBER must contain one to three numeric components\n' >&2
  exit 1
fi

umask 077
KEY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ai-bubble-auth.XXXXXX")"
trap 'rm -rf "$KEY_DIR"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
KEY_PATH="$KEY_DIR/AuthKey.p8"
# Rebuild the PEM so keys stored on one line (spaces instead of newlines) still parse.
printf '%s' "$APP_STORE_CONNECT_API_KEY_P8" | python3 -c '
import re, sys, textwrap
body = re.sub(r"-----[^-]+-----|\\n|\s", "", sys.stdin.read())
print("-----BEGIN PRIVATE KEY-----", *textwrap.wrap(body, 64), "-----END PRIVATE KEY-----", sep="\n")
' > "$KEY_PATH"
unset APP_STORE_CONNECT_API_KEY_P8

cd "$IOS_DIR"
bash scripts/generate_app_icons.sh
xcodegen generate
mkdir -p build
EXPORT_OPTIONS="$KEY_DIR/ExportOptions.plist"
/usr/libexec/PlistBuddy -c 'Clear dict' "$EXPORT_OPTIONS" >/dev/null
/usr/libexec/PlistBuddy -c 'Add :method string app-store-connect' "$EXPORT_OPTIONS"
/usr/libexec/PlistBuddy -c 'Add :destination string upload' "$EXPORT_OPTIONS"
if [[ -n "${PROVISIONING_PROFILE_SPECIFIER:-}" ]]; then
  /usr/libexec/PlistBuddy -c 'Add :signingStyle string manual' "$EXPORT_OPTIONS"
  /usr/libexec/PlistBuddy -c 'Add :signingCertificate string Apple Distribution' "$EXPORT_OPTIONS"
  /usr/libexec/PlistBuddy -c 'Add :provisioningProfiles dict' "$EXPORT_OPTIONS"
  /usr/libexec/PlistBuddy -c "Add :provisioningProfiles:io.github.stresstestor.idlecryptominer string $PROVISIONING_PROFILE_SPECIFIER" "$EXPORT_OPTIONS"
else
  /usr/libexec/PlistBuddy -c 'Add :signingStyle string automatic' "$EXPORT_OPTIONS"
fi
/usr/libexec/PlistBuddy -c "Add :teamID string $APPLE_TEAM_ID" "$EXPORT_OPTIONS"
/usr/libexec/PlistBuddy -c 'Add :manageAppVersionAndBuildNumber bool false' "$EXPORT_OPTIONS"

AUTH_ARGS=(
  -allowProvisioningUpdates
  -authenticationKeyPath "$KEY_PATH"
  -authenticationKeyID "$APP_STORE_CONNECT_KEY_ID"
  -authenticationKeyIssuerID "$APP_STORE_CONNECT_ISSUER_ID"
)

xcodebuild archive \
  -project IdleCryptoMiner.xcodeproj \
  -scheme IdleCryptoMiner \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$IOS_DIR/build/IdleCryptoMiner.xcarchive" \
  "${AUTH_ARGS[@]}" \
  CODE_SIGN_STYLE=Automatic \
  "DEVELOPMENT_TEAM=$APPLE_TEAM_ID" \
  "CURRENT_PROJECT_VERSION=$BUILD_NUMBER"

xcodebuild -exportArchive \
  -archivePath "$IOS_DIR/build/IdleCryptoMiner.xcarchive" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -exportPath "$IOS_DIR/build/testflight" \
  "${AUTH_ARGS[@]}"
