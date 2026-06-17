package com.example.idleminer

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Precision-safe money type for the whole economy.
 *
 * An idle game with a compounding prestige multiplier blows past Double's
 * exact-integer range (~9e15) and silently loses precision in the economy math
 * itself, not just the displayed suffix. BigDecimal keeps every balance exact
 * within [MC]'s significant digits, at sizes far beyond what any player reaches.
 * All of this is pure (no Android, no clocks) so it is unit-tested on the JVM.
 */
typealias Big = BigDecimal

/** 20 significant digits is plenty for display + gameplay and bounds the size. */
internal val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

fun big(v: Long): Big = BigDecimal.valueOf(v)
fun big(v: String): Big = BigDecimal(v)

/** Default boost multiplier (the "overclock"). */
const val BOOST_MULTIPLIER: Long = 2L

/** Offline earnings are capped so a long absence (or a forward clock) can't mint forever. */
const val OFFLINE_CAP_SECONDS: Long = 8L * 60 * 60 // 8 hours

/** Lifetime-hash (this run) needed before a prestige yields its first core. */
const val PRESTIGE_THRESHOLD: String = "1000000" // 1e6

/** Each prestige core grants +10% to all hash production, permanently. */
val COIN_BONUS: Big = BigDecimal("0.10")

/** A manual tap is worth max(1, this fraction of the per-second passive rate). */
val TAP_FRACTION: Big = BigDecimal("0.10")

data class Upgrade(
    val id: String,
    val name: String,
    val baseCost: Big,
    val baseRate: Big,
    val count: Int = 0,
) {
    /** Cost of the NEXT unit: baseCost * COST_GROWTH^count. */
    val currentCost: Big get() = baseCost.multiply(COST_GROWTH.pow(count, MC), MC)

    /** Hash/sec contributed by all owned units of this tier. */
    val currentRate: Big get() = baseRate.multiply(big(count.toLong()), MC)

    companion object {
        val COST_GROWTH: Big = BigDecimal("1.15")
    }
}

/** Total passive hash/sec across all upgrades. */
fun passiveRate(upgrades: List<Upgrade>): Big =
    upgrades.fold(BigDecimal.ZERO) { acc, u -> acc.add(u.currentRate, MC) }

/**
 * Hash gained over [elapsedSec] seconds at [ratePerSec], where [boostedSec] of
 * those seconds were under an active boost giving [boostMultiplier]x.
 *
 * Pure: the caller measures elapsed and boosted seconds from clocks and passes
 * them in. boostedSec is clamped into [0, elapsedSec] so a caller can't
 * over-credit. Non-positive elapsed or rate yields zero.
 */
fun accrue(
    ratePerSec: Big,
    elapsedSec: Long,
    boostedSec: Long,
    boostMultiplier: Long = BOOST_MULTIPLIER,
): Big {
    if (elapsedSec <= 0L || ratePerSec.signum() <= 0) return BigDecimal.ZERO
    val clampedBoost = boostedSec.coerceIn(0L, elapsedSec)
    val effectiveSeconds = elapsedSec + clampedBoost * (boostMultiplier - 1)
    return ratePerSec.multiply(big(effectiveSeconds), MC)
}

/**
 * Offline hash credited for being away [awaySec] real seconds. A non-positive
 * delta (clock moved back, or no time passed) credits nothing; the rest is
 * capped at [OFFLINE_CAP_SECONDS] before any boost overlap is applied.
 */
fun offlineEarnings(ratePerSec: Big, awaySec: Long, boostedSec: Long): Big {
    if (awaySec <= 0L) return BigDecimal.ZERO
    val capped = awaySec.coerceAtMost(OFFLINE_CAP_SECONDS)
    return accrue(ratePerSec, capped, boostedSec.coerceAtMost(capped))
}

/**
 * Seconds of the window [windowStartMs, windowEndMs] that overlap an active
 * boost ending at [boostEndMs]. The boost is assumed to run up to boostEndMs;
 * any part of the window at or before boostEndMs is boosted. Returns whole
 * seconds, never negative.
 */
fun boostedSecondsIn(windowStartMs: Long, windowEndMs: Long, boostEndMs: Long): Long {
    val overlapEnd = minOf(windowEndMs, boostEndMs)
    if (overlapEnd <= windowStartMs) return 0L
    return (overlapEnd - windowStartMs) / 1000L
}

/** Integer floor of the square root of a non-negative [BigInteger] (Newton's method). */
fun isqrt(n: BigInteger): BigInteger {
    require(n.signum() >= 0) { "isqrt of negative" }
    if (n.signum() == 0) return BigInteger.ZERO
    var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
    while (true) {
        val y = (x + n / x).shiftRight(1)
        if (y >= x) return x
        x = y
    }
}

/**
 * Prestige cores granted for [totalEarned] hash earned this run:
 * floor(sqrt(totalEarned / PRESTIGE_THRESHOLD)). Below the threshold, zero.
 * sqrt growth means later cores cost progressively more, so prestige stays a
 * deliberate decision rather than spammable.
 */
fun prestigeCoinsFor(totalEarned: Big): Long {
    val threshold = big(PRESTIGE_THRESHOLD)
    if (totalEarned.compareTo(threshold) < 0) return 0L
    val ratio = totalEarned.divide(threshold, 0, RoundingMode.FLOOR).toBigInteger()
    return isqrt(ratio).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
}

/** Global production multiplier from owned prestige [coins]: 1 + coins * COIN_BONUS. */
fun prestigeMultiplier(coins: Long): Big =
    BigDecimal.ONE.add(big(coins).multiply(COIN_BONUS, MC), MC)

/**
 * Value of one manual tap: max(1, TAP_FRACTION * passiveRate), then the prestige
 * multiplier, then doubled while boosted. Keeps tapping relevant at every stage
 * instead of a flat +1 forever.
 */
fun tapValue(passiveRate: Big, prestigeMult: Big, boosted: Boolean): Big {
    val base = BigDecimal.ONE.max(passiveRate.multiply(TAP_FRACTION, MC))
    val withPrestige = base.multiply(prestigeMult, MC)
    return if (boosted) withPrestige.multiply(big(BOOST_MULTIPLIER), MC) else withPrestige
}

private val SUFFIXES = listOf(
    "", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc",
)

/**
 * Compact number formatting that survives well past billions: integer below
 * 1000, then K/M/B/T/Qa... suffixes up to Dc (1e33), then scientific (1.23e40).
 * Works on the precision-safe [Big] type, not Double.
 */
fun formatBig(value: Big): String {
    if (value.signum() <= 0) return "0"
    if (value.compareTo(BigDecimal(1000)) < 0) {
        return value.setScale(0, RoundingMode.FLOOR).toBigInteger().toString()
    }
    // Integer-part digit count, valid for value >= 1 even when scale is negative.
    val intDigits = value.precision() - value.scale()
    val group = (intDigits - 1) / 3
    if (group < SUFFIXES.size) {
        val mantissa = value.movePointLeft(group * 3).setScale(2, RoundingMode.FLOOR)
        return "${mantissa.toPlainString()}${SUFFIXES[group]}"
    }
    val exp = intDigits - 1
    val mantissa = value.movePointLeft(exp).setScale(2, RoundingMode.FLOOR)
    return "${mantissa.toPlainString()}e$exp"
}
