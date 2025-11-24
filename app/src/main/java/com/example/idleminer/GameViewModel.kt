package com.example.idleminer

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow

val Context.dataStore by preferencesDataStore(name = "game_settings")

data class Upgrade(
    val id: String,
    val name: String,
    val baseCost: Double,
    val baseRate: Double,
    var count: Int = 0
) {
    val currentCost: Double
        get() = baseCost * 1.15.pow(count.toDouble())
    
    val currentRate: Double
        get() = baseRate * count
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val _hash = MutableStateFlow(0.0)
    val hash: StateFlow<Double> = _hash.asStateFlow()

    private val _upgrades = MutableStateFlow<List<Upgrade>>(emptyList())
    val upgrades: StateFlow<List<Upgrade>> = _upgrades.asStateFlow()

    private val _boostEndTime = MutableStateFlow(0L)
    val boostEndTime: StateFlow<Long> = _boostEndTime.asStateFlow()

    private val _offlineEarnings = MutableStateFlow(0.0)
    val offlineEarnings: StateFlow<Double> = _offlineEarnings.asStateFlow()

    private val dataStore = application.dataStore
    
    private val HASH_KEY = doublePreferencesKey("hash")
    private val UPGRADES_KEY = stringPreferencesKey("upgrades") // Format: "id:count,id:count"
    private val LAST_SAVE_KEY = longPreferencesKey("last_save")

    init {
        initializeGame()
    }

    private fun initializeGame() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _hash.value = prefs[HASH_KEY] ?: 0.0
            
            val savedUpgradesStr = prefs[UPGRADES_KEY] ?: ""
            val savedCounts = savedUpgradesStr.split(",")
                .mapNotNull { 
                    val parts = it.split(":")
                    if (parts.size == 2) parts[0] to parts[1].toInt() else null 
                }.toMap()

            val defaultUpgrades = listOf(
                Upgrade("gpu1", "GTX 1050", 50.0, 1.0),
                Upgrade("gpu2", "RTX 4090", 2000.0, 50.0),
                Upgrade("asic", "ASIC Miner", 50000.0, 500.0)
            )

            _upgrades.value = defaultUpgrades.map { upgrade ->
                upgrade.copy(count = savedCounts[upgrade.id] ?: 0)
            }

            val lastSave = prefs[LAST_SAVE_KEY] ?: System.currentTimeMillis()
            val now = System.currentTimeMillis()
            val secondsOffline = (now - lastSave) / 1000
            if (secondsOffline > 10) { // Only count if offline for more than 10s
                val passiveRate = _upgrades.value.sumOf { it.currentRate }
                val earnings = secondsOffline * passiveRate
                if (earnings > 0) {
                    _hash.value += earnings
                    _offlineEarnings.value = earnings
                }
            }

            startGameLoop()
        }
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val isBoosted = now < _boostEndTime.value
                val multiplier = if (isBoosted) 2.0 else 1.0
                
                val passiveRate = _upgrades.value.sumOf { it.currentRate }
                if (passiveRate > 0) {
                    _hash.value += passiveRate * multiplier
                }
                
                delay(1000)
            }
        }
    }

    fun onManualMine() {
        val now = System.currentTimeMillis()
        val isBoosted = now < _boostEndTime.value
        val multiplier = if (isBoosted) 2.0 else 1.0
        _hash.value += 1.0 * multiplier
    }

    fun buyUpgrade(upgradeId: String) {
        val list = _upgrades.value.toMutableList()
        val index = list.indexOfFirst { it.id == upgradeId }
        if (index != -1) {
            val upgrade = list[index]
            if (_hash.value >= upgrade.currentCost) {
                _hash.value -= upgrade.currentCost
                list[index] = upgrade.copy(count = upgrade.count + 1)
                _upgrades.value = list
                saveGame()
            }
        }
    }

    fun activateBoost() {
        _boostEndTime.value = System.currentTimeMillis() + 300_000 // 300 seconds
    }
    
    fun clearOfflineEarnings() {
        _offlineEarnings.value = 0.0
    }

    fun saveGame() {
        viewModelScope.launch {
            val upgradeString = _upgrades.value.joinToString(",") { "${it.id}:${it.count}" }
            dataStore.edit { prefs ->
                prefs[HASH_KEY] = _hash.value
                prefs[UPGRADES_KEY] = upgradeString
                prefs[LAST_SAVE_KEY] = System.currentTimeMillis()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        saveGame()
    }
}
