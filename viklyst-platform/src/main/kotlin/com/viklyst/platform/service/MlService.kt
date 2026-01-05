package com.viklyst.platform.service

import com.viklyst.platform.api.dto.MlPredictRequest
import com.viklyst.platform.api.dto.MlPredictResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class MlService(
    private val restTemplate: RestTemplate,
    @Value("\${viklyst.ml.url:http://localhost:8000}") private val mlBaseUrl: String
) {
    fun predict(symbol: String, from: String, to: String, modelPath: String? = null): MlPredictResponse {
        val url = "${mlBaseUrl.trimEnd('/')}/predict"

        val body = MlPredictRequest(
            symbol = symbol.uppercase(),
            fromDate = from,
            toDate = to,
            modelPath = modelPath
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)

        return restTemplate.postForObject(url, entity, MlPredictResponse::class.java)
            ?: throw IllegalStateException("ML service returned empty response")
    }
}
