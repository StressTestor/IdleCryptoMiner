package com.example.idleminer

import android.app.Application
import android.util.Log

/**
 * Lightweight, dependency-free crash visibility: log every uncaught exception
 * under a stable tag (readable in logcat during the closed test) and then
 * delegate to the platform handler so Play Vitals still records the crash
 * (deobfuscated via the mapping.txt uploaded by the release workflow).
 *
 * A real reporting backend (Crashlytics / Sentry) is the v1.1 upgrade - see
 * docs/LAUNCH_CHECKLIST.md.
 */
class IdleMinerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("IdleCryptoMiner", "Uncaught exception on thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
