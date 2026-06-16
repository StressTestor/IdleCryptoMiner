# architecture

## overview

IdleCryptoMiner is a single-screen Android idle/clicker game. you tap a GPU fan to
mine "hash", spend hash on hardware upgrades that mine passively, and watch a
2x "overclock" boost gate behind a (currently fake) interstitial ad. progress
persists locally and accrues offline earnings while the app is closed.

single Gradle module (`:app`), no backend, no network. all state lives on-device
in DataStore.

## stack

| layer | choice | version |
|-------|--------|---------|
| language | Kotlin | 1.9.0 |
| ui | Jetpack Compose (Material 3) | compose-bom 2023.08.00 |
| compose compiler | kotlinCompilerExtensionVersion | 1.5.1 |
| build | Android Gradle Plugin | 8.1.0 |
| build | Gradle (wrapper) | 8.2 |
| state | Kotlin coroutines + StateFlow | - |
| persistence | AndroidX DataStore (preferences) | 1.0.0 |
| min / target / compile SDK | 24 / 34 / 34 | - |
| ci jdk | Temurin 17 | - |

## directory tree

```
.
├── build.gradle.kts            # root: declares AGP + Kotlin plugins (apply false)
├── settings.gradle.kts         # repos + includes :app, rootProject "IdleCryptoMiner"
├── gradle.properties           # android.useAndroidX, nonTransitiveRClass, jvmargs
├── gradlew / gradlew.bat       # gradle wrapper scripts
├── gradle/wrapper/             # wrapper jar + properties (pins gradle 8.2)
├── .github/workflows/
│   ├── android.yml             # build: assembleDebug + upload APK artifact
│   └── pr-steward.yml          # codex AI PR review/merge automation (reusable wf)
└── app/
    ├── build.gradle.kts        # android config + dependencies
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        └── java/com/example/idleminer/
            ├── MainActivity.kt # all UI: theme, game screen, fan clicker, shop
            ├── GameViewModel.kt# game logic, loop, persistence, offline earnings
            └── AdManager.kt    # interstitial ad interface (stubbed impl)
```

## key patterns

- **single source of truth**: `GameViewModel` (AndroidViewModel) holds all game
  state as `MutableStateFlow`s (hash, upgrades, boostEndTime, offlineEarnings),
  exposed read-only via `asStateFlow()`. the UI collects with
  `collectAsStateWithLifecycle`.
- **game loop**: a `viewModelScope` coroutine ticks every 1000ms, adding passive
  hash rate (x2 while boosted). manual taps add 1 hash per tap.
- **upgrade economy**: cost scales `baseCost * 1.15^count`; rate is `baseRate * count`.
  three upgrades: GTX 1050, RTX 4090, ASIC Miner.
- **persistence**: DataStore preferences. upgrades serialized as `"id:count,id:count"`.
  saved on buy, onPause, onCleared. offline earnings computed on launch from
  `last_save` timestamp delta (ignored under 10s).
- **ads**: `AdManager` is an interface; `AdManagerImpl` is a placeholder that logs
  and waits 1s instead of showing a real ad. swap this for a real SDK (AdMob etc.)
  to monetize.

## persistence schema (DataStore)

| key | type | meaning |
|-----|------|---------|
| `hash` | Double | current hash balance |
| `upgrades` | String | `"gpu1:3,gpu2:1,asic:0"` style id:count pairs |
| `last_save` | Long | epoch millis of last save, used for offline earnings |

## ci

`.github/workflows/android.yml` on push/PR to main|master: checkout, JDK 17,
`./gradlew assembleDebug`, upload `app-debug.apk` as artifact.
`.github/workflows/pr-steward.yml` runs a reusable Codex PR-review workflow on PRs
(needs the `CODEX_ACCESS_TOKEN` secret).

## gotchas

| problem | cause | fix |
|---------|-------|-----|
| CI failed in seconds on every push | no gradle wrapper committed; `./gradlew` didn't exist | committed wrapper (8.2) |
| build fails: "useAndroidX not enabled" | no `gradle.properties` despite all-AndroidX deps | added `android.useAndroidX=true` |
| `collectAsStateWithLifecycle` unresolved | `lifecycle-runtime-compose` dep missing | added the dependency |
| canvas `rotate(degrees, pivot){}` unresolved | only `Modifier.rotate` imported, not the DrawScope one | imported `androidx.compose.ui.graphics.drawscope.rotate` |
| `app/build/` showing up in git | `.gitignore` only ignored root `/build` | added `/app/build` |

## local build

needs JDK 17 to run Gradle 8.2 (newer JDKs can't run AGP 8.1's gradle), plus the
Android SDK (platform 34, build-tools 34.0.0).

```
export JAVA_HOME=<path to jdk 17>
export ANDROID_HOME=<path to android sdk>   # or set sdk.dir in local.properties
./gradlew assembleDebug                       # -> app/build/outputs/apk/debug/app-debug.apk
```

## commands

| task | command |
|------|---------|
| debug build | `./gradlew assembleDebug` |
| install on device | `./gradlew installDebug` |
| lint | `./gradlew lint` |
| clean | `./gradlew clean` |

---
last updated: 2026-06-16
