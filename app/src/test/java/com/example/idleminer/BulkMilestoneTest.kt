package com.example.idleminer

import org.junit.Assert.*
import org.junit.Test

class BulkMilestoneTest {
    private val model = Upgrade("test", "Test", big(10), big(1))

    @Test fun bulkAndBoundaries() {
        assertEquals(0, model.bulkCost(0).signum())
        assertEquals(0, model.bulkCost(2).compareTo(big("21.5")))
        for (n in listOf(1, 10, 100, 1000)) {
            val cost = model.bulkCost(n)
            assertEquals(n, model.maxAffordable(cost))
            assertEquals(n - 1, model.maxAffordable(cost.multiply(big("0.999999"))))
        }
        assertEquals(0, model.maxAffordable(big(9)))
        val huge = big("1e1000")
        val n = model.maxAffordable(huge)
        assertTrue(model.bulkCost(n) <= huge)
        assertTrue(model.bulkCost(n + 1) > huge)
    }

    @Test fun milestonesAndPreview() {
        for ((count, mult) in listOf(0 to 1, 10 to 2, 25 to 4, 50 to 8, 100 to 24, 200 to 72, 500 to 1944)) {
            assertEquals(0, milestoneMultiplier(count).compareTo(big(mult.toLong())))
            val progress = milestoneProgress(count)
            assertTrue(count >= progress.previous && count < progress.next)
        }
        assertEquals(MilestoneProgress(10, 25, 2), milestoneProgress(12))
        assertEquals(0, nextPrestigeThreshold(big(1000000)).compareTo(big(4000000)))
        assertEquals("Pivot now: +1 VC Billions (+10%)\nNext VC Billion in 3.00M run Tokens", prestigePreview(big(1000000)))
        assertEquals("1.00M run Tokens to your first VC Billion", prestigePreview(big(0)))
        assertEquals("1 run Tokens to your first VC Billion", prestigePreview(big(999999)))
    }

    @Test fun oldSaveStillLoads() {
        val save = loadSave(1, "123", "cpu:12", 0, 0, "456", 2)
        assertEquals(12, save.counts["cpu"])
        assertEquals(big(123), save.hash)
        assertEquals(2L, save.prestigeCoins)
    }
}
