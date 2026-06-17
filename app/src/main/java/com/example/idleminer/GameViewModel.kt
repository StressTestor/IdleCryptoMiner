package com.example.idleminer

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal

val Context.dataStore by preferencesDataStore(name = "game_settings")

/** Overclock boost lasts 5 minutes. */
const val BOOST_DURATION_MS: Long = 300_000L

/** Cooldown after a boost ENDS before it can be triggered again. */
const val BOOST_COOLDOWN_MS: Long = 300_000L

/** How often the loop persists state, so a hard kill loses at most this much. */
private const val SAVE_INTERVAL_MS: Long = 20_000L

/** Only surface the offline-earnings dialog after a meaningful absence. */
private const val OFFLINE_DIALOG_MIN_SECONDS: Long = 60L

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _hash = MutableStateFlow(BigDecimal.ZERO as Big)
    val hash: StateFlow<Big> = _hash.asStateFlow()

    private val _upgrades = MutableStateFlow<List<Upgrade>>(emptyList())
    val upgrades: StateFlow<List<Upgrade>> = _upgrades.asStateFlow()

    /** Boost end as an absolute wall-clock timestamp (for UI countdown + persistence). */
    private val _boostEndTime = MutableStateFlow(0L)
    val boostEndTime: StateFlow<Long> = _boostEndTime.asStateFlow()

    private val _offlineEarnings = MutableStateFlow(BigDecimal.ZERO as Big)
    val offlineEarnings: StateFlow<Big> = _offlineEarnings.asStateFlow()

    /** Hash earned since the last prestige - basis for the next prestige reward. */
    private val _runEarned = MutableStateFlow(BigDecimal.ZERO as Big)
    val runEarned: StateFlow<Big> = _runEarned.asStateFlow()

    /** Owned prestige cores (permanent across prestiges). */
    private val _prestigeCoins = MutableStateFlow(0L)
    val prestigeCoins: StateFlow<Long> = _prestigeCoins.asStateFlow()

    /** Sound muted (settings). */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** True until the initial save load completes (drives a loading state). */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val dataStore = application.dataStore

    private val VERSION_KEY = intPreferencesKey("save_version")
    private val HASH_KEY = stringPreferencesKey("hash")
    private val UPGRADES_KEY = stringPreferencesKey("upgrades") // "id:count,id:count"
    private val LAST_SAVE_KEY = longPreferencesKey("last_save")
    private val BOOST_END_KEY = longPreferencesKey("boost_end")
    private val RUN_EARNED_KEY = stringPreferencesKey("run_earned")
    private val PRESTIGE_COINS_KEY = longPreferencesKey("prestige_coins")
    private val MUTE_KEY = booleanPreferencesKey("muted")

    // In-session accrual is anchored to the monotonic clock so loop drift and
    // wall-clock changes can't mis-credit passive income.
    private var lastCreditedElapsed = SystemClock.elapsedRealtime()
    private var lastSaveElapsed = SystemClock.elapsedRealtime()
    // Boost end in the monotonic clock, for in-session boost overlap.
    private var boostEndElapsed = 0L

    init {
        initializeGame()
    }

    private fun prestigeMult(): Big = prestigeMultiplier(_prestigeCoins.value)

    /** Passive hash/sec including the global prestige multiplier. */
    private fun effectiveRate(): Big = passiveRate(_upgrades.value).multiply(prestigeMult(), MC)

    private fun initializeGame() {
        viewModelScope.launch {
            // Crash-proof load: a corrupt/legacy DataStore read falls back to empty.
            val prefs = dataStore.data
                .catch { emit(emptyPreferences()) }
                .first()

            val save = loadSave(
                version = prefs[VERSION_KEY],
                hashRaw = prefs[HASH_KEY],
                upgradesRaw = prefs[UPGRADES_KEY],
                lastSaveWallMs = prefs[LAST_SAVE_KEY],
                boostEndWallMs = prefs[BOOST_END_KEY],
                runEarnedRaw = prefs[RUN_EARNED_KEY],
                prestigeCoins = prefs[PRESTIGE_COINS_KEY],
            )

            _hash.value = save.hash
            _runEarned.value = save.runEarned
            _prestigeCoins.value = save.prestigeCoins
            _muted.value = prefs[MUTE_KEY] ?: false
            _upgrades.value = GameCatalog.withCounts(save.counts)

            val nowWall = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()

            // Restore an in-flight boost if it hasn't expired in wall-clock terms.
            if (save.boostEndWallMs > nowWall) {
                _boostEndTime.value = save.boostEndWallMs
                boostEndElapsed = nowElapsed + (save.boostEndWallMs - nowWall)
            }

            // Offline earnings: capped, ignores a backward clock, accounts for any
            // boost window that overlapped the absence, includes prestige multiplier.
            if (save.lastSaveWallMs > 0L) {
                val awaySec = (nowWall - save.lastSaveWallMs) / 1000L
                val boostedOfflineSec = boostedSecondsIn(
                    save.lastSaveWallMs, nowWall, save.boostEndWallMs,
                )
                val earned = offlineEarnings(effectiveRate(), awaySec, boostedOfflineSec)
                if (earned.signum() > 0) {
                    addHash(earned)
                    if (awaySec >= OFFLINE_DIALOG_MIN_SECONDS) {
                        _offlineEarnings.value = earned
                    }
                }
            }

            lastCreditedElapsed = nowElapsed
            lastSaveElapsed = nowElapsed
            _isLoading.value = false
            startGameLoop()
        }
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val nowElapsed = SystemClock.elapsedRealtime()
                val elapsedSec = (nowElapsed - lastCreditedElapsed) / 1000L
                if (elapsedSec > 0L) {
                    val windowStart = lastCreditedElapsed
                    val windowEnd = windowStart + elapsedSec * 1000L
                    val boostedSec = boostedSecondsIn(windowStart, windowEnd, boostEndElapsed)
                    val gained = accrue(effectiveRate(), elapsedSec, boostedSec)
                    if (gained.signum() > 0) addHash(gained)
                    lastCreditedElapsed = windowEnd // carry the sub-second remainder
                }
                if (nowElapsed - lastSaveElapsed >= SAVE_INTERVAL_MS) {
                    saveGame()
                    lastSaveElapsed = nowElapsed
                }
            }
        }
    }

    private fun addHash(amount: Big) {
        _hash.value = _hash.value.add(amount, MC)
        _runEarned.value = _runEarned.value.add(amount, MC)
    }

    fun onManualMine() {
        val boosted = SystemClock.elapsedRealtime() < boostEndElapsed
        addHash(tapValue(passiveRate(_upgrades.value), prestigeMult(), boosted))
    }

    fun buyUpgrade(upgradeId: String) {
        val list = _upgrades.value.toMutableList()
        val index = list.indexOfFirst { it.id == upgradeId }
        if (index != -1) {
            val upgrade = list[index]
            if (_hash.value >= upgrade.currentCost) {
                _hash.value = _hash.value.subtract(upgrade.currentCost, MC)
                list[index] = upgrade.copy(count = upgrade.count + 1)
                _upgrades.value = list
                saveGame()
            }
        }
    }

    /** Start the overclock boost, unless one is active or still on cooldown. */
    fun activateBoost() {
        // boostEndTime + cooldown covers both the active window and the cooldown.
        if (System.currentTimeMillis() < _boostEndTime.value + BOOST_COOLDOWN_MS) return
        _boostEndTime.value = System.currentTimeMillis() + BOOST_DURATION_MS
        boostEndElapsed = SystemClock.elapsedRealtime() + BOOST_DURATION_MS
        saveGame()
    }

    /**
     * Hard-fork: bank the prestige cores this run has earned, then wipe hash,
     * upgrades and the boost. The permanent prestige multiplier (from owned
     * cores) makes the next run faster. No-op if no full core is available yet.
     */
    fun prestige() {
        val gained = prestigeCoinsFor(_runEarned.value)
        if (gained <= 0L) return
        _prestigeCoins.value += gained
        _hash.value = BigDecimal.ZERO
        _runEarned.value = BigDecimal.ZERO
        _upgrades.value = GameCatalog.withCounts(emptyMap())
        _boostEndTime.value = 0L
        boostEndElapsed = 0L
        saveGame()
    }

    fun clearOfflineEarnings() {
        _offlineEarnings.value = BigDecimal.ZERO
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        viewModelScope.launch {
            runCatching { dataStore.edit { it[MUTE_KEY] = value } }
        }
    }

    /**
     * Wipe all game progress back to a fresh start. Uses DataStore clear() so no
     * stale key can survive into a half-reset (the corrupt-state class M1 guards),
     * then re-writes only the sound preference.
     */
    fun resetProgress() {
        val keepMuted = _muted.value
        _hash.value = BigDecimal.ZERO
        _runEarned.value = BigDecimal.ZERO
        _prestigeCoins.value = 0L
        _upgrades.value = GameCatalog.withCounts(emptyMap())
        _boostEndTime.value = 0L
        boostEndElapsed = 0L
        _offlineEarnings.value = BigDecimal.ZERO
        lastCreditedElapsed = SystemClock.elapsedRealtime()
        lastSaveElapsed = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    prefs.clear()
                    prefs[MUTE_KEY] = keepMuted
                }
            }
        }
    }

    fun saveGame() {
        val hash = _hash.value
        val runEarned = _runEarned.value
        val coins = _prestigeCoins.value
        val counts = _upgrades.value.associate { it.id to it.count }
        val boostEnd = _boostEndTime.value
        viewModelScope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[VERSION_KEY] = SAVE_VERSION
                    prefs[HASH_KEY] = hash.toPlainString()
                    prefs[UPGRADES_KEY] = serializeUpgradeCounts(counts)
                    prefs[LAST_SAVE_KEY] = System.currentTimeMillis()
                    prefs[BOOST_END_KEY] = boostEnd
                    prefs[RUN_EARNED_KEY] = runEarned.toPlainString()
                    prefs[PRESTIGE_COINS_KEY] = coins
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveGame()
    }
}
