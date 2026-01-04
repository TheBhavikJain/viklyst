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
)

