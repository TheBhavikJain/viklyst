package com.viklyst.platform.api

import com.viklyst.platform.domain.BarDaily
import com.viklyst.platform.domain.BarDailyId
import com.viklyst.platform.repo.BarDailyRepository
import com.viklyst.platform.repo.InstrumentRepository
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/instruments")
class ImportController(
    private val instrumentRepo: InstrumentRepository,
    private val barRepo: BarDailyRepository
) {
    @PostMapping("/{symbol}/import/daily", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importDaily(
        @PathVariable symbol: String,
        @RequestPart("file") file: MultipartFile
    ): Map<String, Any> {
        val sym = symbol.trim().uppercase()
        val instrument = instrumentRepo.findBySymbol(sym)
            .orElseThrow { NoSuchElementException("Instrument not found: $sym") }

        val text = file.inputStream.bufferedReader().readText()
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return mapOf("inserted" to 0, "message" to "empty file")

        // Detect header columns (supports common formats)
        val header = lines.first().split(",", ";", "\t").map { it.trim() }
        val delimiter = detectDelimiter(lines.first())

        val rows = lines.drop(1)
        var inserted = 0
        val saved = mutableListOf<BarDaily>()

        for (raw in rows) {
            val parts = raw.split(delimiter).map { it.trim().trim('"') }
            if (parts.size < 5) continue

            // supports headers like Date/Open/High/Low/Close/Volume
            val dateStr = parts[0]
            val day = parseDate(dateStr) ?: continue

            val open = parts[1].toDoubleOrNull() ?: continue
            val high = parts[2].toDoubleOrNull() ?: continue
            val low  = parts[3].toDoubleOrNull() ?: continue
            val close = parts[4].toDoubleOrNull() ?: continue
            val volume = parts.getOrNull(5)?.replace(",", "")?.toLongOrNull()

            saved.add(
                BarDaily(
                    id = BarDailyId(instrumentId = instrument.id, day = day),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )
        }

        // bulk save
        barRepo.saveAll(saved)
        inserted = saved.size

        return mapOf(
            "symbol" to sym,
            "inserted" to inserted
        )
    }

    private fun detectDelimiter(line: String): String =
        when {
            line.contains("\t") -> "\t"
            line.contains(";") -> ";"
            else -> ","
        }

    private fun parseDate(s: String): LocalDate? {
        val trimmed = s.trim()
        val patterns = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,                // 2024-01-31
            DateTimeFormatter.ofPattern("M/d/yyyy"),         // 1/31/2024
            DateTimeFormatter.ofPattern("MM/dd/yyyy")        // 01/31/2024
        )
        for (p in patterns) {
            try { return LocalDate.parse(trimmed, p) } catch (_: Exception) {}
        }
        return null
    }
}
