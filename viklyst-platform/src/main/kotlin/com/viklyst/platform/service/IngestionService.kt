package com.viklyst.platform.service

import com.viklyst.platform.domain.BarDaily
import com.viklyst.platform.domain.BarDailyId
import com.viklyst.platform.marketdata.AlphaVantageClient
import com.viklyst.platform.repo.BarDailyRepository
import com.viklyst.platform.repo.InstrumentRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class IngestionService(
    private val instrumentRepo: InstrumentRepository,
    private val barRepo: BarDailyRepository,
    private val alpha: AlphaVantageClient
) {
    fun ingestDaily(symbol: String, from: LocalDate, to: LocalDate): Int {
        val sym = symbol.trim().uppercase()
        val instrument = instrumentRepo.findBySymbol(sym)
            .orElseThrow { NoSuchElementException("Instrument not found: $sym") }

        // fetch last ~100 days (compact)
        val bars = alpha.fetchDailyCompact(sym)
            .filter { it.day >= from && it.day <= to }

        val entities = bars.map {
            BarDaily(
                id = BarDailyId(instrumentId = instrument.id, day = it.day),
                open = it.open,
                high = it.high,
                low = it.low,
                close = it.close,
                volume = it.volume
            )
        }

        barRepo.saveAll(entities)
        return entities.size
    }
}
