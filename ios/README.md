# Idle AI Bubble for iPhone

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

## Archive for App Store Connect

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
