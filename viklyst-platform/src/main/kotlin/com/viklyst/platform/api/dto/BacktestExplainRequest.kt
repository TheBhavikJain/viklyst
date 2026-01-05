package com.viklyst.platform.api.dto

import java.time.LocalDate

data class BacktestExplainRequest(
    val symbol: String,
    val from: LocalDate,
    val to: LocalDate,
    val lookback: Int = 5,
    val initialCapital: Double = 10_000.0
)
