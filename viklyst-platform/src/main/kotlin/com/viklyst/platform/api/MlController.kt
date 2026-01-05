package com.viklyst.platform.api

import com.viklyst.platform.api.dto.MlPredictResponse
import com.viklyst.platform.service.MlService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.client.RestClientException

@RestController
@RequestMapping("/api/ml")
class MlController(
    private val mlService: MlService
) {

    @GetMapping("/predict")
    fun predict(
        @RequestParam symbol: String,
        @RequestParam("from") from: String,
        @RequestParam("to") to: String,
        @RequestParam(required = false) modelPath: String?
    ): MlPredictResponse {
        try {
            return mlService.predict(symbol, from, to, modelPath)
        } catch (e: RestClientException) {
            // ML service down / unreachable / returned non-2xx
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service error: ${e.message}")
        }
    }
}
