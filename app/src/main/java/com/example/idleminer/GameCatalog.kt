package com.example.idleminer

/**
 * The hardware tiers a fresh game starts with. Kept in one place so balance
 * work (retune + expansion to ~8 tiers) is a single-file edit. Counts are
 * always loaded from the save and overlaid onto these defaults.
 */
object GameCatalog {
    val defaultUpgrades: List<Upgrade> = listOf(
        Upgrade("gpu1", "GTX 1050", baseCost = big("50"), baseRate = big("1")),
        Upgrade("gpu2", "RTX 4090", baseCost = big("2000"), baseRate = big("50")),
        Upgrade("asic", "ASIC Miner", baseCost = big("50000"), baseRate = big("500")),
    )

    /** Apply saved counts onto the default catalog, ignoring unknown ids. */
    fun withCounts(counts: Map<String, Int>): List<Upgrade> =
        defaultUpgrades.map { it.copy(count = counts[it.id] ?: 0) }
}
