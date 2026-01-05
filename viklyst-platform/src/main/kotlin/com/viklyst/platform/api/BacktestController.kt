package com.viklyst.platform.api

import com.viklyst.platform.backtest.BacktestResult
import com.viklyst.platform.service.BacktestService
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import com.viklyst.platform.api.dto.BacktestExplainFacts
import com.viklyst.platform.api.dto.BacktestExplainRequest
import org.springframework.web.bind.annotation.*
import kotlin.math.round

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

    @RestController
    @RequestMapping("/api/backtests")
    class BacktestExplainController(
        private val service: BacktestService
    ) {

        @PostMapping("/explain/facts")
        fun explainFacts(@RequestBody req: BacktestExplainRequest): BacktestExplainFacts {
            val resp = service.nextDayMomentumCurve(
                symbol = req.symbol,
                from = req.from,
                to = req.to,
                lookback = req.lookback,
                initialCapital = req.initialCapital
            )

            val strategyEnding = resp.curve.lastOrNull()?.equity ?: req.initialCapital
            val benchEnding = resp.benchmark.lastOrNull()?.equity ?: req.initialCapital

            // Your summary already contains strategy return/drawdown in %.
            // For benchmark return/drawdown, compute from benchmark equity series:
            val benchEq = resp.benchmark.map { it.equity }
            val benchReturnPct = round2(((benchEq.last() / benchEq.first()) - 1.0) * 100.0)
            val benchMaxDdPct = round2(maxDrawdownPct(benchEq))

            return BacktestExplainFacts(
                symbol = req.symbol.uppercase(),
                from = req.from,
                to = req.to,
                lookback = req.lookback,
                initialCapital = req.initialCapital,

                strategyTotalReturnPct = resp.summary.totalReturnPct,
                benchmarkTotalReturnPct = benchReturnPct,
                strategyMaxDrawdownPct = resp.summary.maxDrawdownPct,
                benchmarkMaxDrawdownPct = benchMaxDdPct,
                trades = resp.summary.trades,
                winRatePct = resp.summary.winRate,

                strategyEndingEquity = round2(strategyEnding),
                benchmarkEndingEquity = round2(benchEnding)
            )
        }

        private fun round2(x: Double): Double = round(x * 100.0) / 100.0

        private fun maxDrawdownPct(equity: List<Double>): Double {
            var peak = equity.firstOrNull() ?: return 0.0
            var maxDd = 0.0
            for (v in equity) {
                if (v > peak) peak = v
                val dd = if (peak <= 0.0) 0.0 else ((peak - v) / peak) * 100.0
                if (dd > maxDd) maxDd = dd
            }
            return maxDd
        }
    }

}
