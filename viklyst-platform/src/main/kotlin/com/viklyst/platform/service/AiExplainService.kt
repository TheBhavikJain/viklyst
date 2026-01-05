package com.viklyst.platform.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExplainDto(
    val symbol: String,
    val from_date: String,
    val to_date: String,
    val threshold: Double,
    val explanation: String
)

@Service
class AiExplainService {
    private val rest = RestTemplate()
    private val mlBaseUrl = System.getenv("VIKLYST_ML_URL") ?: "http://localhost:8000"

    fun explain(symbol: String, from: LocalDate, to: LocalDate, threshold: Double): ExplainDto {
        val url = UriComponentsBuilder
            .fromHttpUrl("$mlBaseUrl/explain")
            .toUriString()

        val body = mapOf(
            "symbol" to symbol.trim().uppercase(),
            "from_date" to from.toString(),
            "to_date" to to.toString(),
            "threshold" to threshold
        )

        return rest.postForObject(url, body, ExplainDto::class.java)
            ?: throw IllegalStateException("AI service returned null")
    }
}
