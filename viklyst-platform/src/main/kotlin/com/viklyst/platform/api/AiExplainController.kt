package com.viklyst.platform.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import com.viklyst.platform.service.AiExplainService

@RestController
class AiExplainController(
    private val aiExplainService: AiExplainService
) {
    @GetMapping("/api/ai/explain")
    fun explain(
        @RequestParam symbol: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
        @RequestParam(defaultValue = "0.55") threshold: Double
    ) = aiExplainService.explain(symbol, from, to, threshold)
}
