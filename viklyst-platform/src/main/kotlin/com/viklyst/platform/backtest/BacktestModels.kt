package com.viklyst.platform.backtest

import java.time.LocalDate

data class Candle(
    val day: LocalDate,
    val close: Double
)

data class BacktestResult(
    val symbol: String,
    val from: LocalDate,
    val to: LocalDate,
    val strategy: String,
    val trades: Int,
    val winRate: Double,
    val totalReturnPct: Double,
    val maxDrawdownPct: Double,
    val points: Int,
    val initialCapital: Double = 10_000.0,
    val endingEquity: Double = 0.0,
    val exposurePct: Double = 0.0
) {
    companion object {

        // For results that are multiplier-based curves (1.0, 1.03, ...)
        fun fromMultipliers(
            symbol: String,
            from: LocalDate,
            to: LocalDate,
            strategy: String,
            trades: Int,
            winRate: Double,
            totalReturnPct: Double,
            maxDrawdownPct: Double,
            points: Int,
            initialCapital: Double,
            endingMultiplier: Double,
            exposurePct: Double
        ): BacktestResult {
            val endingEquity = initialCapital * endingMultiplier
            return BacktestResult(
                symbol = symbol,
                from = from,
                to = to,
                strategy = strategy,
                trades = trades,
                winRate = winRate,
                totalReturnPct = totalReturnPct,
                maxDrawdownPct = maxDrawdownPct,
                points = points,
                initialCapital = initialCapital,
                endingEquity = endingEquity,
                exposurePct = exposurePct
            )
        }

        // For results where you already have endingEquity as a number
        fun withEquity(
            symbol: String,
            from: LocalDate,
            to: LocalDate,
            strategy: String,
            trades: Int,
            winRate: Double,
            totalReturnPct: Double,
            maxDrawdownPct: Double,
            points: Int,
            initialCapital: Double,
            endingEquity: Double,
            exposurePct: Double
        ): BacktestResult =
            BacktestResult(
                symbol = symbol,
                from = from,
                to = to,
                strategy = strategy,
                trades = trades,
                winRate = winRate,
                totalReturnPct = totalReturnPct,
                maxDrawdownPct = maxDrawdownPct,
                points = points,
                initialCapital = initialCapital,
                endingEquity = endingEquity,
                exposurePct = exposurePct
            )
    }
}


