package com.viklyst.platform.service

import com.viklyst.platform.backtest.BacktestCurveResponse
import com.viklyst.platform.backtest.BacktestResult
import com.viklyst.platform.backtest.DrawdownPoint
import com.viklyst.platform.backtest.EquityPoint
import com.viklyst.platform.repo.BarDailyRepository
import com.viklyst.platform.repo.InstrumentRepository
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.round
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

@JsonIgnoreProperties(ignoreUnknown = true)
data class MlPredictDto(
    val symbol: String,
    val model_file: String? = null,
    val as_of: String,
    val prob_up: Double,
    val predicted: Int
)

@Service
class MlBacktestService(
    private val instrumentRepo: InstrumentRepository,
    private val barRepo: BarDailyRepository,
) {
    private val rest = RestTemplate()

    // if you already have this configured somewhere else, reuse it
    private val mlBaseUrl = System.getenv("VIKLYST_ML_URL") ?: "http://localhost:8000"

    fun mlCurve(symbol: String, from: LocalDate, to: LocalDate, threshold: Double, initialCapital: Double): BacktestCurveResponse {
        val sym = symbol.trim().uppercase()
        val instrument = instrumentRepo.findBySymbol(sym).orElseThrow { NoSuchElementException("Instrument not found: $sym") }
        val bars = barRepo.findRange(instrument.id, from, to)
        if (bars.size < 15) throw IllegalArgumentException("Not enough bars in range (need ~15+ for features)")

        // We will simulate "predict at end of day i -> trade day i+1"
        var equity = initialCapital
        var peak = initialCapital
        var trades = 0
        var wins = 0

        val curve = mutableListOf<EquityPoint>()
        val bench = mutableListOf<EquityPoint>()

        // benchmark buy&hold
        val startClose = bars.first().close
        var benchEquity = initialCapital

        for (i in 0 until bars.size) {
            val day = bars[i].id.day
            val close = bars[i].close

            // benchmark equity
            benchEquity = round2(initialCapital * (close / startClose))
            bench.add(EquityPoint(day, benchEquity))

            // strategy equity point (mark-to-market)
            curve.add(EquityPoint(day, round2(equity)))
        }

        val warmup = 12 // because ma_10 / vol_10 need ~10 days, 12 is safer

        // trades occur from i -> i+1, so loop until second last
        for (i in warmup until bars.size - 1) {
            val asOf = bars[i].id.day
            val nextDay = bars[i + 1].id.day
            val nextClose = bars[i + 1].close
            val todayClose = bars[i].close

            val probUp = callMlProb(sym, from, asOf)
            if (probUp != null && probUp >= threshold) {
                trades++
                val dailyRet = (nextClose / todayClose)
                val oldEquity = equity
                equity = round2(equity * dailyRet)
                if (equity > oldEquity) wins++
            }

            // record equity at nextDay (so the curve actually moves over time)
            val nextIdx = i + 1
            curve[nextIdx] = EquityPoint(nextDay, round2(equity))
        }

        // rebuild final curve using end equity (simple: last mark-to-market)
        curve[curve.lastIndex] = EquityPoint(bars.last().id.day, round2(equity))

        val dd = computeDrawdown(curve)
        val bdd = computeDrawdown(bench)

        val winRatePct = if (trades == 0) 0.0 else round2((wins.toDouble() / trades.toDouble()) * 100.0)
        val totalReturnPct = round2(((equity - initialCapital) / initialCapital) * 100.0)
        val maxDrawdownPct = dd.maxOfOrNull { it.drawdownPct } ?: 0.0

        val summary = BacktestResult(
            symbol = sym,
            from = from,
            to = to,
            strategy = "ML_THRESHOLD_${threshold}",
            trades = trades,
            winRate = winRatePct,
            totalReturnPct = totalReturnPct,
            maxDrawdownPct = maxDrawdownPct,
            points = curve.size,
            initialCapital = initialCapital,
            endingEquity = round2(equity),
            exposurePct = 0.0
        )

        return BacktestCurveResponse(
            summary = summary,
            curve = curve,
            benchmark = bench,
            drawdown = dd,
            benchmarkDrawdown = bdd
        )
    }

    private fun callMlProb(symbol: String, from: LocalDate, to: LocalDate): Double? {
        val url = UriComponentsBuilder
            .fromHttpUrl("$mlBaseUrl/predict")
            .toUriString()

        val body = mapOf(
            "symbol" to symbol,
            "from_date" to from.toString(),
            "to_date" to to.toString()
        )

        return try {
            val resp = rest.postForObject(url, body, MlPredictDto::class.java)
                ?: return null
            resp.prob_up
        } catch (e: HttpClientErrorException.BadRequest) {
            // ML server says: not enough bars after feature engineering (or similar)
            null
        } catch (e: HttpClientErrorException) {
            // other 4xx
            null
        } catch (e: HttpServerErrorException) {
            // ML server 5xx
            null
        } catch (e: ResourceAccessException) {
            // ML server down / connection refused / timeout
            null
        }
    }

    private fun computeDrawdown(curve: List<EquityPoint>): List<DrawdownPoint> {
        var peak = 0.0
        return curve.map {
            peak = max(peak, it.equity)
            val ddPct = if (peak <= 0.0) 0.0 else ((peak - it.equity) / peak) * 100.0
            DrawdownPoint(it.day, round2(ddPct))
        }
    }

    private fun round2(x: Double): Double = round(x * 100.0) / 100.0
}
