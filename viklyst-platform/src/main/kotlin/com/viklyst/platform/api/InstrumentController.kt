package com.viklyst.platform.api

import com.viklyst.platform.api.dto.CreateInstrumentRequest
import com.viklyst.platform.api.dto.InstrumentResponse
import com.viklyst.platform.domain.Instrument
import com.viklyst.platform.repo.InstrumentRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/instruments")
class InstrumentController(
    private val repo: InstrumentRepository
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateInstrumentRequest): InstrumentResponse {
        val symbol = req.symbol.trim().uppercase()

        // prevent duplicates
        repo.findBySymbol(symbol).ifPresent {
            throw IllegalArgumentException("Instrument already exists: $symbol")
        }

        val saved = repo.save(Instrument(symbol = symbol, name = req.name?.trim()))
        return InstrumentResponse(saved.id, saved.symbol, saved.name)
    }

    @GetMapping("/{symbol}")
    fun get(@PathVariable symbol: String): InstrumentResponse {
        val sym = symbol.trim().uppercase()
        val inst = repo.findBySymbol(sym).orElseThrow { NoSuchElementException("Not found: $sym")}
        return InstrumentResponse(inst.id, inst.symbol, inst.name)
    }

    @GetMapping
    fun list(): List<InstrumentResponse> =
        repo.findAll().map { InstrumentResponse(it.id, it.symbol, it.name)}
}