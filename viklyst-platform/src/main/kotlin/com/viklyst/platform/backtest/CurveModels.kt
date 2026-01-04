package com.viklyst.platform.backtest

import java.time.LocalDate

data class EquityPoint(
    val day: LocalDate,
    val equity: Double // will be USD now
)

data class DrawdownPoint(
    val day: LocalDate,
    val drawdownPct: Double // positive number like 3.28
)

data class BacktestCurveResponse(
    val summary: BacktestResult,
    val curve: List<EquityPoint>,
    val benchmark: List<EquityPoint>,
    val drawdown: List<DrawdownPoint>,
    val benchmarkDrawdown: List<DrawdownPoint>
)
