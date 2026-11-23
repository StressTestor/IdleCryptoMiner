# AI Bubble for iPhone

Native SwiftUI iPhone build of the same offline idle/clicker game as the Android
app.

## What is included

- single-screen SwiftUI game UI
- tap-to-prompt chat button
- eight passive parody model tiers
- free 2x Hype Cycle boost with cooldown
- Funding Round VC Billions
- offline earnings capped at 8 hours
- local-only save data via `UserDefaults`
- reset progress and sound/haptics toggle
- `PrivacyInfo.xcprivacy` declaring no collected data and the UserDefaults
  required-reason API
- XcodeGen project config for generating an Xcode project on macOS

## Build on your Mac

Install Xcode from the Mac App Store first.

```bash
brew install xcodegen
cd ios
bash scripts/generate_app_icons.sh
xcodegen generate
open IdleCryptoMiner.xcodeproj
```

In Xcode:

1. Select the `IdleCryptoMiner` target.
2. Set your Apple Developer Team under Signing & Capabilities.
3. Keep the bundle identifier as `io.github.stresstestor.idlecryptominer`
   unless you intentionally want a different App Store record.
4. Pick an iPhone simulator or your plugged-in iPhone and run.

## Tests and simulator tester build

After generating icons and the project, run both economy and UI tests:

```bash
xcodebuild test -project IdleCryptoMiner.xcodeproj -scheme IdleCryptoMiner \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' CODE_SIGNING_ALLOWED=NO
```

The shared scheme runs `IdleCryptoMinerTests` and `IdleCryptoMinerUITests`.
Debug builds accept `--uitesting --fresh-save` or `--uitesting --seed-save`
(1,000,000 tokens and run earnings). `--uitesting --offline-save` ages the
existing save by two minutes to exercise real offline loading on relaunch.
These hooks are compiled out of Release and never affect normal launches.

CI uploads `AI-Bubble-Simulator.zip`. This is **simulator-only**, not an iPhone
or TestFlight install. On an Apple Silicon Mac with Xcode and a booted iOS simulator:

```bash
unzip AI-Bubble-Simulator.zip
xcrun simctl install booted IdleCryptoMiner.app
xcrun simctl launch booted io.github.stresstestor.idlecryptominer
```

To build the same zip locally:

```bash
xcodebuild build -project IdleCryptoMiner.xcodeproj -scheme IdleCryptoMiner \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath build CODE_SIGNING_ALLOWED=NO
ditto -c -k --sequesterRsrc --keepParent build/Build/Products/Debug-iphonesimulator/IdleCryptoMiner.app AI-Bubble-Simulator.zip
```

Device/TestFlight distribution additionally requires an Apple Developer team,
signing certificate/private key and provisioning profile (or configured automatic
signing), plus App Store Connect access and an app record. The regular simulator
CI workflow does not attempt a signed device build.

## Signed device archive

For automatic signing and a direct TestFlight upload, run
`bash ios/scripts/testflight.sh` from the repository root on macOS. Set
`APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_ID`,
`APP_STORE_CONNECT_API_KEY_P8` (the full private `.p8` contents), and
`APPLE_TEAM_ID` in the environment; never commit these values. The script
temporarily writes the private key with owner-only permissions and removes it
on exit. `BUILD_NUMBER` overrides `CURRENT_PROJECT_VERSION` for the archive
without editing `project.yml`; it defaults to a UTC timestamp. The export uses
`destination: upload`, so running the script actually uploads, not just exports.

For an API key without cloud-managed distribution certificate access, install an
Apple Distribution certificate with its private key in the login keychain
(grant `codesign` access), and install the matching App Store provisioning
profile in `~/Library/Developer/Xcode/UserData/Provisioning Profiles/`.
Set `PROVISIONING_PROFILE_SPECIFIER="AI Bubble App Store"` to export with manual
signing using that profile and the local Apple Distribution identity. Archiving
still uses automatic signing. Leave this variable unset for the original
automatic export signing behavior. These local signing assets are not supplied
by the four API-key secrets and must also be installed on any CI runner using
manual export signing.

The manual **iOS TestFlight** GitHub Actions workflow uses those same four
repository secrets and `github.run_number` as its build number. Configure an
App Store Connect app record and an API key with appropriate upload/signing
permissions before dispatching it. Ensure the build number exceeds prior
uploads (especially after a local timestamp-numbered upload).

After the simulator/device smoke test passes:

```bash
cd ios
xcodegen generate
xcodebuild \
  -project IdleCryptoMiner.xcodeproj \
  -scheme IdleCryptoMiner \
  -destination 'generic/platform=iOS' \
  -archivePath build/IdleCryptoMiner.xcarchive \
  archive
```

Then open the archive in Xcode Organizer and use Distribute App -> App Store
Connect.

The app has no network code, no ads, no IAP, and no real money or gambling. No real AI was harmed. Not affiliated with any actual AI company.
The App Store listing and review notes should say that plainly.
