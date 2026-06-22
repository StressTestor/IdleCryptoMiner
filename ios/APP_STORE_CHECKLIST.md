# App Store checklist

## App identity

- Name: Idle Crypto Miner
- Bundle ID: `io.github.stresstestor.idlecryptominer`
- SKU suggestion: `idlecryptominer-ios`
- Primary category: Games
- Secondary category suggestion: Simulation or Strategy
- Age rating: answer as a simulation/clicker game; no real money, gambling, or
  unrestricted web access.

## Privacy

- Privacy Policy URL: required for iOS apps in App Store Connect.
- App Privacy: no data collected, no tracking.
- Data deletion answer: progress is stored locally only; users can use Settings
  -> Reset Progress or uninstall the app.
- Privacy manifest: `IdleCryptoMiner/PrivacyInfo.xcprivacy`
  - `NSPrivacyTracking`: false
  - `NSPrivacyCollectedDataTypes`: empty
  - `NSPrivacyAccessedAPICategoryUserDefaults`: `CA92.1` for local game saves

## Review notes

Suggested review note:

```text
Idle Crypto Miner is an offline idle/clicker simulation game. It does not mine
real cryptocurrency, handle money, offer gambling, connect to a backend, use ads,
or include in-app purchases. All progress is stored locally on the device and can
be reset from Settings.
```

## Assets

- Run `bash scripts/generate_app_icons.sh` before archiving.
- Prepare iPhone screenshots from the simulator.
- Feature art is optional for App Store, but useful for marketing.

## Upload flow

1. Generate icons.
2. Generate the Xcode project with `xcodegen generate`.
3. Set Signing & Capabilities to your paid Apple Developer team.
4. Run on simulator and at least one real iPhone.
5. Product -> Archive.
6. Distribute App -> App Store Connect.
7. Wait for build processing.
8. Fill privacy, rating, screenshots, description, and review notes.
9. Submit for App Review.
