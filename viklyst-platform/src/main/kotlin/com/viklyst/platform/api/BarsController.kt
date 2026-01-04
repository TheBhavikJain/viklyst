package com.viklyst.platform.api

import com.viklyst.platform.repo.BarDailyRepository
import com.viklyst.platform.repo.InstrumentRepository
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

data class BarDailyResponse(
    val day: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long?
)

@RestController
@RequestMapping("/api/instruments")
class BarsController(
    private val instrumentRepo: InstrumentRepository,
    private val barRepo: BarDailyRepository
) {
    @GetMapping("/{symbol}/bars/daily")
    fun getDailyBars(
        @PathVariable symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate
    ): List<BarDailyResponse> {
        val sym = symbol.trim().uppercase()
        val instrument = instrumentRepo.findBySymbol(sym)
            .orElseThrow { NoSuchElementException("Instrument not found: $sym") }

        return barRepo.findRange(instrument.id, from, to).map {
            BarDailyResponse(
                day = it.id.day,
                open = it.open,
                high = it.high,
                low = it.low,
                close = it.close,
                volume = it.volume
            )
        }
    }
}
