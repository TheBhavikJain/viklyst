package com.viklyst.platform.marketdata

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

data class DailyBarDto(
    val day: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

@Component
class AlphaVantageClient(
    @Value("\${alphavantage.base-url}") private val baseUrl: String,
    @Value("\${alphavantage.api-key}") private val apiKey: String
) {
    private val webClient = WebClient.builder().baseUrl(baseUrl).build()

    /**
     * Free key can only reliably use outputsize=compact (latest ~100 points) for TIME_SERIES_DAILY.
     * outputsize=full requires premium. (Alpha Vantage docs)
     */
    fun fetchDailyCompact(symbol: String): List<DailyBarDto> {
        val json: JsonNode = webClient.get()
            .uri { b ->
                b.path("/query")
                    .queryParam("function", "TIME_SERIES_DAILY")
                    .queryParam("symbol", symbol)
                    .queryParam("outputsize", "compact")
                    .queryParam("apikey", apiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .block() ?: throw IllegalStateException("Empty response from Alpha Vantage")

        // Alpha Vantage may return "Note" when you hit rate limits, or "Error Message" for invalid symbols/keys.
        json.get("Error Message")?.let { throw IllegalArgumentException(it.asText()) }
        json.get("Note")?.let { throw IllegalStateException("Alpha Vantage rate-limited: ${it.asText()}") }

        val timeSeries = json.get("Time Series (Daily)")
            ?: throw IllegalStateException("Missing Time Series (Daily) in response")

        val out = mutableListOf<DailyBarDto>()

        // timeSeries fields are like: "2026-01-02": { "1. open": "...", ... }
        val fields = timeSeries.fields()
        while (fields.hasNext()) {
            val (dateStr, node) = fields.next()
            val day = LocalDate.parse(dateStr)

            val open = node.get("1. open").asText().toDouble()
            val high = node.get("2. high").asText().toDouble()
            val low  = node.get("3. low").asText().toDouble()
            val close = node.get("4. close").asText().toDouble()
            val volume = node.get("5. volume").asText().toLong()

            out.add(DailyBarDto(day, open, high, low, close, volume))
        }

        return out.sortedBy { it.day } // ascending
    }
}
