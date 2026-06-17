package com.example.idleminer

/**
 * The hardware tiers a fresh game starts with. Counts are always loaded from the
 * save and overlaid onto these defaults.
 *
 * Balance: cost steps ~x12 and rate steps ~x15 per tier, so base efficiency
 * (rate / cost) STRICTLY INCREASES up the ladder - every newly affordable tier
 * is the best value, fixing the old bug where the flagship ASIC was the worst
 * deal. [efficienciesStrictlyIncrease] is asserted in tests.
 */
object GameCatalog {
    val defaultUpgrades: List<Upgrade> = listOf(
        Upgrade("cpu", "CPU Miner", baseCost = big("10"), baseRate = big("0.1")),
        Upgrade("gpu1", "GTX 1050", baseCost = big("120"), baseRate = big("1.5")),
        Upgrade("gpu2", "RTX 4090", baseCost = big("1500"), baseRate = big("24")),
        Upgrade("asic", "ASIC Miner", baseCost = big("18000"), baseRate = big("360")),
        Upgrade("rig", "Mining Rig", baseCost = big("220000"), baseRate = big("5500")),
        Upgrade("rack", "Server Rack", baseCost = big("2600000"), baseRate = big("83000")),
        Upgrade("datacenter", "Data Center", baseCost = big("32000000"), baseRate = big("1300000")),
        Upgrade("quantum", "Quantum Miner", baseCost = big("400000000"), baseRate = big("20000000")),
    )

    /** Apply saved counts onto the default catalog, ignoring unknown ids. */
    fun withCounts(counts: Map<String, Int>): List<Upgrade> =
        defaultUpgrades.map { it.copy(count = counts[it.id] ?: 0) }
}
