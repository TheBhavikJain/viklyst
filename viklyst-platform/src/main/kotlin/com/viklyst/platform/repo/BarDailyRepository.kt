package com.viklyst.platform.repo

import com.viklyst.platform.domain.BarDaily
import com.viklyst.platform.domain.BarDailyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface BarDailyRepository : JpaRepository<BarDaily, BarDailyId> {

    @Query("""
        select b from BarDaily b
        where b.id.instrumentId = :instrumentId
          and b.id.day >= :from
          and b.id.day <= :to
        order by b.id.day asc
    """)
    fun findRange(
        @Param("instrumentId") instrumentId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<BarDaily>
}
