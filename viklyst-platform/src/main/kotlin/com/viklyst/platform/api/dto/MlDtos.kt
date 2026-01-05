package com.viklyst.platform.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class MlPredictRequest(
    val symbol: String,
    @JsonProperty("from_date") val fromDate: String,
    @JsonProperty("to_date") val toDate: String,
    @JsonProperty("model_path") val modelPath: String? = null
)

data class MlPredictResponse(
    val symbol: String,
    @JsonProperty("model_file") val modelFile: String,
    @JsonProperty("as_of") val asOf: String,
    @JsonProperty("prob_up") val probUp: Double,
    val predicted: Int
)
