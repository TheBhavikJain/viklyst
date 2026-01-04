package com.viklyst.platform.repo

import com.viklyst.platform.domain.Instrument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface InstrumentRepository : JpaRepository<Instrument, Long> {
    fun findBySymbol(symbol: String): Optional<Instrument>
}