package com.example.idleminer

import java.math.BigDecimal

/** Bump when the persisted shape changes; [migrate] handles older saves. */
const val SAVE_VERSION: Int = 1

/**
 * The full persisted game state, in pure form. The ViewModel reads/writes
 * individual DataStore keys and funnels them through the parse/serialize
 * helpers here so every load path is crash-proof and unit-testable - a
 * partial, legacy, or corrupt save must never crash the app.
 */
data class SaveData(
    val version: Int = SAVE_VERSION,
    val hash: Big = BigDecimal.ZERO,
    val counts: Map<String, Int> = emptyMap(),
    val lastSaveWallMs: Long = 0L,
    val boostEndWallMs: Long = 0L,
    val lifetimeHash: Big = BigDecimal.ZERO,
)

/**
 * Parse the "id:count,id:count" upgrade-counts string defensively. Anything
 * malformed (missing colon, non-numeric count, negative count, blank id) is
 * dropped rather than throwing. Null/blank input yields an empty map.
 */
fun parseUpgradeCounts(raw: String?): Map<String, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(",").mapNotNull { token ->
        val parts = token.split(":")
        if (parts.size != 2) return@mapNotNull null
        val id = parts[0].trim()
        val count = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
        if (id.isEmpty() || count < 0) null else id to count
    }.toMap()
}

/** Serialize upgrade counts back to the "id:count,id:count" string. */
fun serializeUpgradeCounts(counts: Map<String, Int>): String =
    counts.entries.joinToString(",") { "${it.key}:${it.value}" }

/** Parse a BigDecimal string defensively, falling back to [default] on garbage. */
fun parseBig(raw: String?, default: Big = BigDecimal.ZERO): Big {
    if (raw.isNullOrBlank()) return default
    return runCatching { BigDecimal(raw) }.getOrNull()?.takeIf { it.signum() >= 0 } ?: default
}

/**
 * Build a [SaveData] from raw persisted values, crash-proof. Used on load.
 * [version] missing/garbage is treated as the current version (a fresh save).
 */
fun loadSave(
    version: Int?,
    hashRaw: String?,
    upgradesRaw: String?,
    lastSaveWallMs: Long?,
    boostEndWallMs: Long?,
    lifetimeHashRaw: String?,
): SaveData {
    val raw = SaveData(
        version = version ?: SAVE_VERSION,
        hash = parseBig(hashRaw),
        counts = parseUpgradeCounts(upgradesRaw),
        lastSaveWallMs = (lastSaveWallMs ?: 0L).coerceAtLeast(0L),
        boostEndWallMs = (boostEndWallMs ?: 0L).coerceAtLeast(0L),
        lifetimeHash = parseBig(lifetimeHashRaw),
    )
    return migrate(raw)
}

/**
 * Migrate an older save forward. With version 1 and no live users this is a
 * pass-through, but the hook exists so the first real schema change has a home
 * instead of crashing existing players.
 */
fun migrate(save: SaveData): SaveData = when (save.version) {
    SAVE_VERSION -> save
    else -> save.copy(version = SAVE_VERSION)
}
