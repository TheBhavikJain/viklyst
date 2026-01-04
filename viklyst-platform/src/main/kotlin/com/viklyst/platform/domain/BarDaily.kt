package com.viklyst.platform.domain

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class BarDailyId(
    @Column(name = "instrument_id")
    val instrumentId: Long = 0,

    @Column(name = "day")
    val day: LocalDate = LocalDate.MIN
) : Serializable

@Entity
@Table(name = "bar_daily")
data class BarDaily(
    @EmbeddedId
    val id: BarDailyId,

    @Column(nullable = false)
    val open: Double,

    @Column(nullable = false)
    val high: Double,

    @Column(nullable = false)
    val low: Double,

    @Column(nullable = false)
    val close: Double,

    val volume: Long? = null
)
