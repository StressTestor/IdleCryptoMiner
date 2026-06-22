# IdleCryptoMiner

Offline idle/clicker crypto-mining simulation game.

The Android app lives in `app/`. The native iPhone/App Store scaffold lives in
`ios/`.

## Game loop

- tap the GPU fan to mine Hash
- buy hardware tiers for passive Hash/sec
- trigger a free 2x Overclock boost with cooldown
- Hard Fork to reset the run and bank permanent production cores
- progress persists locally, with capped offline earnings

No backend. No real cryptocurrency mining. No real money. No gambling.

## Android

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

See `ARCHITECTURE.md`, `docs/RELEASE.md`, and `docs/LAUNCH_CHECKLIST.md`.

## iPhone

```bash
brew install xcodegen
cd ios
bash scripts/generate_app_icons.sh
xcodegen generate
open IdleCryptoMiner.xcodeproj
```

See `ios/README.md` and `ios/APP_STORE_CHECKLIST.md`.
