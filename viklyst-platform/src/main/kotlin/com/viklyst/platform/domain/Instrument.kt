package com.viklyst.platform.domain

import jakarta.persistence.*

@Entity
@Table(name = "instrument")
data class Instrument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, unique = true)
    val symbol: String,
    
    val name: String? = null
)
