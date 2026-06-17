package com.example.idleminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCatalogTest {

    @Test fun has_eight_tiers_with_unique_ids() {
        val ups = GameCatalog.defaultUpgrades
        assertEquals(8, ups.size)
        assertEquals(ups.size, ups.map { it.id }.toSet().size)
    }

    @Test fun base_efficiency_strictly_increases_up_the_ladder() {
        // rate / cost must rise tier over tier, so the newest affordable tier is
        // always the best value (the old ASIC-is-worst bug must not return).
        val effs = GameCatalog.defaultUpgrades.map { it.baseRate.divide(it.baseCost, MC) }
        for (i in 1 until effs.size) {
            assertTrue(
                "tier $i efficiency ${effs[i]} must exceed tier ${i - 1} ${effs[i - 1]}",
                effs[i].compareTo(effs[i - 1]) > 0,
            )
        }
    }

    @Test fun cost_and_rate_increase_monotonically() {
        val ups = GameCatalog.defaultUpgrades
        for (i in 1 until ups.size) {
            assertTrue(ups[i].baseCost.compareTo(ups[i - 1].baseCost) > 0)
            assertTrue(ups[i].baseRate.compareTo(ups[i - 1].baseRate) > 0)
        }
    }

    @Test fun with_counts_overlays_and_ignores_unknown_ids() {
        val result = GameCatalog.withCounts(mapOf("gpu1" to 5, "nonexistent" to 99))
        assertEquals(5, result.first { it.id == "gpu1" }.count)
        assertTrue(result.none { it.id == "nonexistent" })
        assertEquals(0, result.first { it.id == "quantum" }.count)
    }
}
