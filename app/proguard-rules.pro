# R8 / ProGuard rules for IdleCryptoMiner.
#
# AndroidX, Jetpack Compose, kotlinx.coroutines and DataStore all ship their own
# consumer R8 rules, so the app shrinks safely with very few manual keeps. Add
# app-specific keeps below if something breaks under minify.
#
# IMPORTANT: R8 issues only surface at runtime. ALWAYS smoke-test the minified
# release build (assembleRelease / bundleRelease) on a real device before
# shipping, not just "it compiled".

# Keep Kotlin metadata so reflection-light tooling and coroutines stay correct.
-keep class kotlin.Metadata { *; }

# kotlinx.coroutines - defensive keeps for ServiceLoader-loaded internals.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

# Keep line numbers for readable crash stack traces (paired with the uploaded
# mapping.txt), and hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
