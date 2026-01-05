package com.viklyst.platform.api.dto

import java.time.LocalDate

data class BacktestExplainFacts(
    val symbol: String,
    val from: LocalDate,
    val to: LocalDate,
    val lookback: Int,
    val initialCapital: Double,

    val strategyTotalReturnPct: Double,
    val benchmarkTotalReturnPct: Double,
    val strategyMaxDrawdownPct: Double,
    val benchmarkMaxDrawdownPct: Double,
    val trades: Int,
    val winRatePct: Double,

    // last values (ending equity)
    val strategyEndingEquity: Double,
    val benchmarkEndingEquity: Double
)
