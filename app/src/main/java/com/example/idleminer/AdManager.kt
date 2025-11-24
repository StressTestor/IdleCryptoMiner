package com.example.idleminer

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface AdManager {
    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit)
}

class AdManagerImpl : AdManager {
    override fun showInterstitial(activity: Activity, onAdClosed: () -> Unit) {
        Log.d("AdManager", "Showing Interstitial Ad...")
        // Simulate ad display time
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000) // 1 second "ad"
            Log.d("AdManager", "Ad Closed")
            onAdClosed()
        }
    }
}
