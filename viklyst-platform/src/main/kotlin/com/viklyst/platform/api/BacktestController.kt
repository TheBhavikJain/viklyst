package com.viklyst.platform.api

import com.viklyst.platform.backtest.BacktestResult
import com.viklyst.platform.service.BacktestService
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/backtests/baseline")
class BacktestController(
    private val service: BacktestService
) {
    @GetMapping("/buy-and-hold")
    fun buyAndHold(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate
    ): BacktestResult =
        service.buyAndHold(symbol, from, to)

    @GetMapping("/next-day-momentum")
    fun nextDayMomentum(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(defaultValue = "5") lookback: Int
    ): BacktestResult =
        service.nextDayMomentum(symbol, from, to, lookback)

    @GetMapping("/next-day-momentum/curve")
    fun nextDayMomentumCurve(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(defaultValue = "5") lookback: Int
    ) = service.nextDayMomentumCurve(symbol, from, to, lookback)

}
