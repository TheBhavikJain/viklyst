package com.viklyst.platform.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateInstrumentRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val symbol: String,

    @field:Size(max = 200)
    val name: String?= null
)

data class InstrumentResponse(
    val id: Long,
    val symbol: String,
    val name: String?
)