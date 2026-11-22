# Idle AI Bubble

Offline idle/clicker AI-boom parody simulation game.

The Android app lives in `app/`. The native iPhone/App Store scaffold lives in
`ios/`.

## Game loop

- tap the Prompt button to generate Tokens
- train parody model tiers for passive Tokens/sec
- trigger a free 2x Hype Cycle boost with cooldown
- Funding Round to reset the run and bank permanent VC Billions
- progress persists locally, with capped offline earnings

No backend. No real AI was harmed. Not affiliated with any actual AI company. No real money. No gambling.

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
