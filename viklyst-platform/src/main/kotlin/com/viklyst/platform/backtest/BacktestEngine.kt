package com.viklyst.platform.backtest

import kotlin.math.max
import kotlin.math.min

object BacktestEngine {

    /**
     * Buy & Hold from first close to last close.
     * Equity starts at 1.0 and becomes close[i]/close[0]
     */
    fun buyAndHold(candles: List<Candle>): Pair<List<Double>, Int> {
        if (candles.size < 2) return Pair(emptyList(), 0)
        val base = candles.first().close
        val equity = candles.map { it.close / base }
        return Pair(equity, 1) // treat as 1 "trade"
    }

    /**
     * Next-day momentum:
     * - Compute return over lookback days: close[t] / close[t-lookback] - 1
     * - If > 0 => hold long tomorrow (earn next day's return)
     * - Else => stay in cash tomorrow (0 return)
     *
     * Equity starts at 1.0
     */
    fun nextDayMomentum(candles: List<Candle>, lookback: Int): Pair<List<Double>, TradeStats> {
        if (candles.size < lookback + 2) return Pair(emptyList(), TradeStats(0, 0))

        var equity = 1.0
        val curve = ArrayList<Double>(candles.size)
        curve.add(equity)

        var trades = 0
        var wins = 0

        // i is "today" index; we decide position for i+1 day
        for (i in lookback until candles.size - 1) {
            val todayClose = candles[i].close
            val pastClose = candles[i - lookback].close
            val momentum = (todayClose / pastClose) - 1.0

            val nextClose = candles[i + 1].close
            val nextReturn = (nextClose / todayClose) - 1.0

            val takeTrade = momentum > 0.0
            if (takeTrade) {
                trades++
                if (nextReturn > 0.0) wins++
                equity *= (1.0 + nextReturn)
            }
            // if not trading, equity unchanged

            curve.add(equity)
        }

        return Pair(curve, TradeStats(trades, wins))
    }

    data class TradeStats(val trades: Int, val wins: Int)

    fun maxDrawdownPct(equity: List<Double>): Double {
        if (equity.isEmpty()) return 0.0
        var peak = equity.first()
        var maxDd = 0.0
        for (v in equity) {
            peak = max(peak, v)
            val dd = (peak - v) / peak
            maxDd = max(maxDd, dd)
        }
        return maxDd * 100.0
    }

    fun totalReturnPct(equity: List<Double>): Double {
        if (equity.isEmpty()) return 0.0
        val start = equity.first()
        val end = equity.last()
        return ((end / start) - 1.0) * 100.0
    }

    fun winRate(stats: TradeStats): Double {
        if (stats.trades == 0) return 0.0
        return (stats.wins.toDouble() / stats.trades.toDouble()) * 100.0
    }

    fun buyAndHoldCurve(candles: List<Candle>): List<EquityPoint> {
        if (candles.size < 2) return emptyList()
        val base = candles.first().close
        return candles.map { EquityPoint(it.day, round4(it.close / base)) }
    }

    fun nextDayMomentumCurve(candles: List<Candle>, lookback: Int): Pair<List<EquityPoint>, TradeStats> {
        if (candles.size < lookback + 2) return Pair(emptyList(), TradeStats(0, 0))

        var equity = 1.0
        val points = mutableListOf(EquityPoint(candles[lookback].day, equity))

        var trades = 0
        var wins = 0

        for (i in lookback until candles.size - 1) {
            val today = candles[i]
            val past = candles[i - lookback]
            val momentum = (today.close / past.close) - 1.0

            val next = candles[i + 1]
            val nextReturn = (next.close / today.close) - 1.0

            val takeTrade = momentum > 0.0
            if (takeTrade) {
                trades++
                if (nextReturn > 0.0) wins++
                equity *= (1.0 + nextReturn)
            }

            points.add(EquityPoint(next.day, round4(equity)))
        }

        return Pair(points, TradeStats(trades, wins))
    }

    private fun round4(x: Double) = kotlin.math.round(x * 10000.0) / 10000.0
}
