package com.example.idleminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SaveDataTest {

    @Test fun parses_valid_counts() {
        assertEquals(mapOf("gpu1" to 3, "gpu2" to 1), parseUpgradeCounts("gpu1:3,gpu2:1"))
    }

    @Test fun drops_malformed_tokens_without_crashing() {
        // non-numeric count, missing colon, too many colons, negative, blank id
        assertEquals(
            mapOf("gpu1" to 3, "gpu2" to 2),
            parseUpgradeCounts("gpu1:3,broken,gpu2:2,asic:abc,x:3:4,bad:-5,:9"),
        )
    }

    @Test fun blank_or_null_counts_is_empty() {
        assertEquals(emptyMap<String, Int>(), parseUpgradeCounts(""))
        assertEquals(emptyMap<String, Int>(), parseUpgradeCounts(null))
        assertEquals(emptyMap<String, Int>(), parseUpgradeCounts("   "))
    }

    @Test fun counts_round_trip() {
        val counts = mapOf("gpu1" to 3, "asic" to 7)
        assertEquals(counts, parseUpgradeCounts(serializeUpgradeCounts(counts)))
    }

    @Test fun parses_big_defensively() {
        assertTrue(BigDecimal("123.5").compareTo(parseBig("123.5")) == 0)
        assertTrue(BigDecimal.ZERO.compareTo(parseBig("not-a-number")) == 0)
        assertTrue(BigDecimal.ZERO.compareTo(parseBig(null)) == 0)
        assertTrue(BigDecimal.ZERO.compareTo(parseBig("-5")) == 0) // negative -> default
        assertTrue(BigDecimal("7").compareTo(parseBig("", big("7"))) == 0)
    }

    @Test fun load_assembles_state() {
        val save = loadSave(
            version = 1,
            hashRaw = "1234.5",
            upgradesRaw = "gpu1:2",
            lastSaveWallMs = 1_000L,
            boostEndWallMs = 2_000L,
            lifetimeHashRaw = "9999",
        )
        assertEquals(1, save.version)
        assertTrue(BigDecimal("1234.5").compareTo(save.hash) == 0)
        assertEquals(mapOf("gpu1" to 2), save.counts)
        assertEquals(1_000L, save.lastSaveWallMs)
        assertEquals(2_000L, save.boostEndWallMs)
        assertTrue(BigDecimal("9999").compareTo(save.lifetimeHash) == 0)
    }

    @Test fun load_handles_missing_and_garbage_values() {
        val save = loadSave(
            version = null,
            hashRaw = null,
            upgradesRaw = "garbage-no-colon",
            lastSaveWallMs = -50L,     // negative coerced to 0
            boostEndWallMs = null,
            lifetimeHashRaw = "??",
        )
        assertEquals(SAVE_VERSION, save.version)
        assertTrue(BigDecimal.ZERO.compareTo(save.hash) == 0)
        assertEquals(emptyMap<String, Int>(), save.counts)
        assertEquals(0L, save.lastSaveWallMs)
        assertEquals(0L, save.boostEndWallMs)
    }

    @Test fun migrate_is_passthrough_for_current_version() {
        val save = SaveData(version = SAVE_VERSION, hash = big("5"))
        assertEquals(save, migrate(save))
    }

    @Test fun migrate_bumps_unknown_version() {
        val old = SaveData(version = 999)
        assertEquals(SAVE_VERSION, migrate(old).version)
    }
}
