# release & signing

how IdleCryptoMiner gets from source to a signed artifact. read this before the
first Play upload - the keystore decision is permanent.

## app identity

- `applicationId` is `io.github.stresstestor.idlecryptominer`.
- this is **permanent** after the first Play publish. it cannot be changed for the
  same listing, ever. `namespace` (`com.example.idleminer`) and the Kotlin package
  are internal-only and do not affect Play.

## keys: two of them, don't confuse them

with Play App Signing (enroll on first upload - recommended), there are two keys:

| key | who holds it | rotatable? | what it does |
|-----|-------------|-----------|--------------|
| app signing key | Google | no | signs the APKs delivered to devices |
| upload key | you | yes (via Play support) | signs the `.aab` you upload to Play |

CI and local release builds only ever use the **upload key**. if it leaks or is
lost, Google can reset it. losing the app signing key is impossible once Google
holds it - that's the whole point of enrolling.

## generate the upload keystore (one time)

```
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

then, NON-NEGOTIABLE:

- store the `.jks` file AND its passwords off-machine (password manager + an
  encrypted backup). not in git, not only on this laptop.
- the repo `.gitignore` already blocks `*.jks`, `*.keystore`, and
  `keystore.properties`. keep it that way.

## how the build finds the key

`app/build.gradle.kts` reads signing config from, in order:

1. environment variables (used by CI):
   - `KEYSTORE_FILE` - path to the `.jks`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
2. a `keystore.properties` file at the repo root (used for local release builds):

   ```properties
   storeFile=/absolute/path/to/upload-keystore.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

if neither is present, the release build is produced **unsigned** (fine for
verifying that minify/R8 works; not uploadable to Play).

## build commands

```
./gradlew assembleDebug      # debug apk, what CI builds today
./gradlew assembleRelease    # minified release apk (R8 + resource shrink)
./gradlew bundleRelease      # signed .aab for Play upload (needs the key)
```

release builds have `isMinifyEnabled` and `isShrinkResources` on. always
smoke-test the minified build on a real device - R8 problems only show at runtime.

## toolchain

local release builds need JDK 17 (AGP 8.7 / Gradle 8.9 run on 17; newer JDKs do
not yet run this Gradle), and the Android SDK with platform 35 + build-tools 35.

```
export JAVA_HOME=<jdk 17 home>
export ANDROID_HOME=<android sdk>
```
