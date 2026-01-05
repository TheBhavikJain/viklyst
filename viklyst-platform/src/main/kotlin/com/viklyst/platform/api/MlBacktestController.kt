package com.viklyst.platform.api

import com.viklyst.platform.service.MlBacktestService
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/backtests/ml")
class MlBacktestController(
    private val mlBacktestService: MlBacktestService
) {
    @GetMapping("/curve")
    fun curve(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(defaultValue = "0.55") threshold: Double,
        @RequestParam(defaultValue = "10000") initialCapital: Double
    ) = mlBacktestService.mlCurve(symbol, from, to, threshold, initialCapital)
}
