package com.example.idleminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertBig(expected: String, actual: Big, msg: String = "") =
    assertTrue("$msg expected=$expected actual=${actual.toPlainString()}",
        BigDecimal(expected).compareTo(actual) == 0)

class EconomyTest {

    private val gpu = Upgrade("gpu1", "GTX 1050", baseCost = big("50"), baseRate = big("1"))

    @Test fun cost_scales_by_growth_factor() {
        assertBig("50", gpu.copy(count = 0).currentCost)
        assertBig("57.5", gpu.copy(count = 1).currentCost)       // 50 * 1.15
        assertBig("66.125", gpu.copy(count = 2).currentCost)     // 50 * 1.15^2
    }

    @Test fun rate_scales_with_count() {
        assertBig("0", gpu.copy(count = 0).currentRate)
        assertBig("3", gpu.copy(count = 3).currentRate)
    }

    @Test fun passive_rate_sums_all_tiers() {
        val ups = listOf(
            gpu.copy(count = 2),                                   // 1 * 2 = 2
            Upgrade("a", "A", big("2000"), big("50"), count = 1),  // 50
        )
        assertBig("52", passiveRate(ups))
    }

    @Test fun accrue_basic() {
        assertBig("50", accrue(big("10"), elapsedSec = 5, boostedSec = 0))
    }

    @Test fun accrue_boost_doubles_boosted_seconds() {
        // 5s total, 2s boosted at 2x -> 10 * (5 + 2) = 70
        assertBig("70", accrue(big("10"), elapsedSec = 5, boostedSec = 2))
    }

    @Test fun accrue_clamps_boost_to_elapsed() {
        // boostedSec > elapsedSec is clamped -> all 5s boosted -> 10 * (5 + 5) = 100
        assertBig("100", accrue(big("10"), elapsedSec = 5, boostedSec = 99))
    }

    @Test fun accrue_zero_for_nonpositive_inputs() {
        assertBig("0", accrue(big("10"), elapsedSec = 0, boostedSec = 0))
        assertBig("0", accrue(big("10"), elapsedSec = -5, boostedSec = 0))
        assertBig("0", accrue(BigDecimal.ZERO, elapsedSec = 5, boostedSec = 0))
    }

    @Test fun offline_is_capped() {
        val rate = big("1")
        // away beyond the cap credits exactly the cap
        assertBig(OFFLINE_CAP_SECONDS.toString(),
            offlineEarnings(rate, awaySec = OFFLINE_CAP_SECONDS + 10_000, boostedSec = 0))
    }

    @Test fun offline_ignores_backward_clock() {
        assertBig("0", offlineEarnings(big("100"), awaySec = -42, boostedSec = 0))
        assertBig("0", offlineEarnings(big("100"), awaySec = 0, boostedSec = 0))
    }

    @Test fun offline_accounts_for_boost_overlap() {
        // 100s away, 10s of it boosted -> 1 * (100 + 10) = 110
        assertBig("110", offlineEarnings(big("1"), awaySec = 100, boostedSec = 10))
    }

    @Test fun boosted_seconds_overlap() {
        assertEquals(3L, boostedSecondsIn(windowStartMs = 1_000, windowEndMs = 6_000, boostEndMs = 4_000))
        assertEquals(5L, boostedSecondsIn(windowStartMs = 1_000, windowEndMs = 6_000, boostEndMs = 10_000))
        assertEquals(0L, boostedSecondsIn(windowStartMs = 1_000, windowEndMs = 6_000, boostEndMs = 500))
        assertEquals(0L, boostedSecondsIn(windowStartMs = 1_000, windowEndMs = 1_000, boostEndMs = 9_000))
    }

    @Test fun format_below_thousand_is_integer() {
        assertEquals("0", formatBig(big("0")))
        assertEquals("999", formatBig(big("999")))
        assertEquals("999", formatBig(big("999.9")))
    }

    @Test fun format_uses_suffixes() {
        assertEquals("1.00K", formatBig(big("1000")))
        assertEquals("1.23K", formatBig(big("1234")))
        assertEquals("1.50M", formatBig(big("1500000")))
        assertEquals("1.00B", formatBig(big("1000000000")))
        assertEquals("1.00T", formatBig(big("1000000000000")))
    }

    @Test fun format_falls_back_to_scientific_past_table() {
        // 1e36 is past the Dc (1e33) suffix -> scientific
        assertEquals("1.00e36", formatBig(BigDecimal.ONE.movePointRight(36)))
    }
}
