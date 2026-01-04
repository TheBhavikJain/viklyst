package com.viklyst.platform.service

import com.viklyst.platform.backtest.BacktestCurveResponse
import com.viklyst.platform.backtest.BacktestEngine
import com.viklyst.platform.backtest.BacktestResult
import com.viklyst.platform.backtest.Candle
import com.viklyst.platform.backtest.DrawdownPoint
import com.viklyst.platform.backtest.EquityPoint
import com.viklyst.platform.repo.BarDailyRepository
import com.viklyst.platform.repo.InstrumentRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.round

@Service
class BacktestService(
    private val instrumentRepo: InstrumentRepository,
    private val barRepo: BarDailyRepository
) {

    fun buyAndHold(symbol: String, from: LocalDate, to: LocalDate): BacktestResult {
        val candles = loadCandles(symbol, from, to)
        val (equity, trades) = BacktestEngine.buyAndHold(candles)

        return BacktestResult(
            symbol = symbol.uppercase(),
            from = from,
            to = to,
            strategy = "BUY_AND_HOLD",
            trades = trades,
            winRate = 0.0,
            totalReturnPct = round2(BacktestEngine.totalReturnPct(equity)),
            maxDrawdownPct = round2(BacktestEngine.maxDrawdownPct(equity)),
            points = equity.size
        )
    }

    fun nextDayMomentum(symbol: String, from: LocalDate, to: LocalDate, lookback: Int): BacktestResult {
        val candles = loadCandles(symbol, from, to)
        val (equity, stats) = BacktestEngine.nextDayMomentum(candles, lookback)

        val initialCapital = 10_000.0
        val endingEquity = round2(initialCapital * (equity.lastOrNull() ?: 1.0))

        // approx: percent of days where we took a trade
        val exposurePct =
            if (equity.size <= 1) 0.0
            else round2((stats.trades.toDouble() / (equity.size - 1).toDouble()) * 100.0)

        return BacktestResult(
            symbol = symbol.uppercase(),
            from = from,
            to = to,
            strategy = "NEXT_DAY_MOMENTUM_LB_$lookback",
            trades = stats.trades,
            winRate = round2(BacktestEngine.winRate(stats)),
            totalReturnPct = round2(BacktestEngine.totalReturnPct(equity)),
            maxDrawdownPct = round2(BacktestEngine.maxDrawdownPct(equity)),
            points = equity.size,
            initialCapital = initialCapital,
            endingEquity = endingEquity,
            exposurePct = exposurePct
        )
    }


    // Optional helper so old UI calls still work:
    fun nextDayMomentumCurve(symbol: String, from: LocalDate, to: LocalDate, lookback: Int): BacktestCurveResponse {
        return nextDayMomentumCurve(symbol, from, to, lookback, initialCapital = 10_000.0)
    }

    fun nextDayMomentumCurve(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        lookback: Int,
        initialCapital: Double
    ): BacktestCurveResponse {
        val candles = loadCandles(symbol, from, to)

        // multiplier curves (1.0, 1.03, 0.98...)
        val benchmarkMult = BacktestEngine.buyAndHoldCurve(candles)
        val (strategyMult, stats) = BacktestEngine.nextDayMomentumCurve(candles, lookback)

        // convert multipliers -> USD
        val curveUsd = toUsdCurve(strategyMult, initialCapital)
        val benchUsd = toUsdCurve(benchmarkMult, initialCapital)

        // drawdown series (in %)
        val drawdown = computeDrawdown(curveUsd)
        val benchDrawdown = computeDrawdown(benchUsd)

        val summary = BacktestResult(
            symbol = symbol.uppercase(),
            from = from,
            to = to,
            strategy = "NEXT_DAY_MOMENTUM_LB_$lookback",
            trades = stats.trades,
            winRate = round2(BacktestEngine.winRate(stats)),
            totalReturnPct = round2(BacktestEngine.totalReturnPct(strategyMult.map { it.equity })),
            maxDrawdownPct = round2(BacktestEngine.maxDrawdownPct(strategyMult.map { it.equity })),
            points = curveUsd.size
        )

        return BacktestCurveResponse(
            summary = summary,
            curve = curveUsd,
            benchmark = benchUsd,
            drawdown = drawdown,
            benchmarkDrawdown = benchDrawdown
        )
    }

    private fun loadCandles(symbol: String, from: LocalDate, to: LocalDate): List<Candle> {
        val sym = symbol.trim().uppercase()
        val instrument = instrumentRepo.findBySymbol(sym)
            .orElseThrow { NoSuchElementException("Instrument not found: $sym") }

        val bars = barRepo.findRange(instrument.id, from, to)
        if (bars.size < 2) throw IllegalArgumentException("Not enough bars in range")

        return bars.map { Candle(day = it.id.day, close = it.close) }
    }

    private fun toUsdCurve(mult: List<EquityPoint>, initialCapital: Double): List<EquityPoint> {
        return mult.map { EquityPoint(it.day, round2(it.equity * initialCapital)) }
    }

    private fun computeDrawdown(curveUsd: List<EquityPoint>): List<DrawdownPoint> {
        var peak = 0.0
        return curveUsd.map {
            peak = kotlin.math.max(peak, it.equity)
            val ddPct = if (peak <= 0.0) 0.0 else ((peak - it.equity) / peak) * 100.0
            DrawdownPoint(it.day, round2(ddPct))
        }
    }

    private fun round2(x: Double): Double = round(x * 100.0) / 100.0
}
