package com.example.idleminer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Thin SoundPool wrapper for the short UI sounds. No-ops while [muted]. The
 * owner (the Composable) sets [muted] from persisted state and calls [release]
 * on dispose.
 */
class SoundManager(context: Context) {
    @Volatile
    var muted: Boolean = false

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val tapId = pool.load(context, R.raw.sfx_tap, 1)
    private val buyId = pool.load(context, R.raw.sfx_buy, 1)
    private val forkId = pool.load(context, R.raw.sfx_fork, 1)

    private fun play(id: Int) {
        if (!muted && id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun tap() = play(tapId)
    fun buy() = play(buyId)
    fun fork() = play(forkId)
    fun release() = pool.release()
}
