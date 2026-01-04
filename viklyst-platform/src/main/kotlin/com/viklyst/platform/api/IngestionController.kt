package com.viklyst.platform.api

import com.viklyst.platform.service.IngestionService
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/ingest")
class IngestController(
    private val ingestionService: IngestionService
) {
    @PostMapping("/daily")
    fun ingestDaily(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate
    ): Map<String, Any> {
        val inserted = ingestionService.ingestDaily(symbol, from, to)
        return mapOf("symbol" to symbol.uppercase(), "inserted" to inserted)
    }
}
