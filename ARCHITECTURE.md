# architecture

## overview

IdleCryptoMiner is a single-screen Android idle/clicker game. you tap a GPU fan to
mine "hash", spend hash on 8 tiers of hardware that mine passively, trigger a free
cooldown-gated 2x "overclock" boost, and "hard fork" (prestige) for a permanent
global multiplier. progress persists locally and accrues capped offline earnings
while the app is closed.

single Gradle module (`:app`), no backend, no network, fully offline. all state
lives on-device in DataStore. ad-free in v1 (the `AdManager` seam is kept for v1.1).

## stack

| layer | choice | version |
|-------|--------|---------|
| language | Kotlin | 2.0.21 (Compose compiler plugin) |
| ui | Jetpack Compose (Material 3) | compose-bom 2024.09.03 |
| build | Android Gradle Plugin | 8.7.3 |
| build | Gradle (wrapper) | 8.9 |
| state | Kotlin coroutines + StateFlow | - |
| persistence | AndroidX DataStore (preferences) | 1.1.1 |
| money type | java.math.BigDecimal (MathContext 20) | - |
| audio | android.media.SoundPool | - |
| min / target / compile SDK | 24 / 35 / 35 | - |
| ci jdk | Temurin 17 | - |

## directory tree

```
.
├── build.gradle.kts            # root: AGP + Kotlin + Compose-compiler plugins (apply false)
├── settings.gradle.kts         # repos + includes :app
├── gradle.properties           # androidx, nonTransitiveRClass, VERSION_CODE/NAME
├── gradlew / gradle/wrapper/   # gradle 8.9 wrapper
├── keystore.properties         # local release signing (gitignored, optional)
├── .conductor/settings.toml    # Conductor workspace setup/run/archive scripts
├── .github/workflows/
│   ├── android.yml             # PR/push: unit tests + lint + assembleDebug (pinned SHAs)
│   ├── release.yml             # tag v*: signed bundleRelease + mapping.txt upload
│   └── pr-steward.yml          # codex AI PR review/merge automation (reusable wf)
├── docs/
│   ├── RELEASE.md              # keystore custody + signing
│   └── LAUNCH_CHECKLIST.md     # human-gated Play Store steps
├── store-assets/               # 512 store icon (listing asset, not in APK)
└── app/
    ├── proguard-rules.pro      # R8 keeps (minify + resource shrink on release)
    ├── build.gradle.kts        # android config, signing, deps
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # app-owned theme, icon, allowBackup=false, no INTERNET
        │   ├── res/
        │   │   ├── values/           # strings, colors, themes
        │   │   ├── drawable/         # adaptive-icon vectors
        │   │   ├── mipmap-*/         # launcher icon (bitmaps + anydpi-v26 adaptive)
        │   │   └── raw/              # sfx_tap/buy/fork.wav
        │   └── java/com/example/idleminer/
        │       ├── IdleMinerApp.kt   # Application: uncaught-exception logger
        │       ├── MainActivity.kt   # all Compose UI: theme, screen, fan, shop, dialogs, settings
        │       ├── GameViewModel.kt  # state, loop, persistence, prestige, settings
        │       ├── Economy.kt        # PURE: Big economy, accrue, prestige, formatBig (tested)
        │       ├── SaveData.kt       # PURE: versioned crash-proof parse/serialize (tested)
        │       ├── GameCatalog.kt    # the 8 default tiers (tested for efficiency monotonicity)
        │       ├── SoundManager.kt   # SoundPool wrapper (mute-aware)
        │       └── AdManager.kt      # ad seam (unused in v1, kept for v1.1)
        └── test/java/com/example/idleminer/
            ├── EconomyTest.kt        # economy/prestige/tap/format (cost, accrue, isqrt...)
            ├── SaveDataTest.kt       # defensive parse, load, reset/fresh-state
            └── GameCatalogTest.kt    # tier efficiency strictly increases
```

## key patterns

- **pure core, testable on the JVM**: all economy + save logic lives in
  `Economy.kt` / `SaveData.kt` / `GameCatalog.kt` with no Android or clock
  dependencies, so it is unit-tested without instrumentation. the ViewModel is a
  thin Android shell over it.
- **precision-safe money**: every balance is `BigDecimal` (`typealias Big`) with a
  shared `MathContext(20)`. Double would lose precision past ~9e15, which the
  compounding prestige multiplier blows through.
- **single source of truth**: `GameViewModel` (AndroidViewModel) exposes all state
  as read-only `StateFlow`s (hash, upgrades, boostEndTime, offlineEarnings,
  runEarned, prestigeCoins, muted, isLoading). the UI collects with
  `collectAsStateWithLifecycle`.
- **monotonic accrual**: the loop credits passive income by measured
  `SystemClock.elapsedRealtime()` elapsed (immune to loop drift and clock
  changes), carrying the sub-second remainder. offline earnings use wall-clock
  delta, capped at 8h, ignoring backward clock jumps, boost-overlap aware.
- **prestige loop**: cores = `floor(sqrt(runEarned / 1e6))`; each core = +10% to
  all production permanently; `prestige()` banks cores and wipes the run.
- **persistence**: DataStore preferences, versioned + crash-proof load (a
  corrupt/legacy save can't crash the app). debounced save (~20s) in the loop +
  on buy/boost/pause/clear. `resetProgress()` uses `clear()` so no key survives.
- **boost**: free, cooldown-gated (5 min active, 5 min cooldown), state derived
  from the persisted `boost_end` timestamp; a per-second UI ticker drives the
  countdown.

## persistence schema (DataStore)

| key | type | meaning |
|-----|------|---------|
| `save_version` | Int | save format version (migrate hook) |
| `hash` | String | current hash balance (BigDecimal) |
| `upgrades` | String | `"cpu:3,gpu1:1,..."` id:count pairs |
| `last_save` | Long | epoch millis of last save (offline earnings) |
| `boost_end` | Long | epoch millis the overclock boost ends |
| `run_earned` | String | hash earned since last prestige (BigDecimal) |
| `prestige_coins` | Long | owned prestige cores |
| `muted` | Boolean | sound setting |

## ci

- `android.yml` (PR/push to main|master): JDK 17, `testDebugUnitTest` + `lintDebug`
  + `assembleDebug`, uploads APK + reports. fails the build on any test/lint
  failure. actions pinned to commit SHAs.
- `release.yml` (tag `v*` / manual): decodes the upload keystore from secrets,
  `bundleRelease`, uploads the signed `.aab` + `mapping.txt`. tag-only so fork PRs
  never see the signing secrets.
- `pr-steward.yml`: reusable Codex PR-review workflow (needs `CODEX_ACCESS_TOKEN`).

## gotchas

| problem | cause | fix |
|---------|-------|-----|
| `applicationId` vs `namespace` | only `applicationId` (`io.github.stresstestor.idlecryptominer`) is Play-facing/permanent; `namespace`/package stay `com.example.idleminer` | changed applicationId only, no source rename |
| minify needs keep rules | `proguard-rules.pro` is inert unless minify is on | minify + resource shrink + real proguard rules all on for release |
| nested scroll crash | a `LazyColumn` inside a `verticalScroll` Column throws | shop is a plain `Column` (only 8 items) |
| content under system bars | targetSdk 35 forces edge-to-edge | `enableEdgeToEdge()` + `windowInsetsPadding(systemBars)` |
| local JDK | Gradle 8.9 / AGP 8.7 don't run on the newest JDKs | build with JDK 17 (`/usr/libexec/java_home -v 17`) |

## local build

needs JDK 17 + the Android SDK (platform 35, build-tools 35.0.0).

```
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

## commands

| task | command |
|------|---------|
| debug build | `./gradlew assembleDebug` |
| unit tests | `./gradlew testDebugUnitTest` |
| lint | `./gradlew lintDebug` |
| signed release bundle | `./gradlew bundleRelease` (needs the keystore, see docs/RELEASE.md) |
| install on device | `./gradlew installDebug` |
| clean | `./gradlew clean` |

see `docs/RELEASE.md` for signing and `docs/LAUNCH_CHECKLIST.md` for the Play
Store steps.

---
last updated: 2026-06-17
