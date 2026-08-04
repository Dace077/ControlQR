package com.controlqr.acceso.data

import com.controlqr.acceso.core.Formats
import com.controlqr.acceso.data.db.PassEntity
import com.controlqr.acceso.data.db.PassStatus
import java.time.LocalDate

enum class ReportPeriod(val title: String) {
    DIA("Por día"),
    SEMANA("Por semana"),
    MES("Por mes")
}

data class ReportBucket(
    val label: String,
    val start: Long,
    val end: Long,
    val entries: Int,
    val exits: Int,
    val issued: Int,
    /** QR vigentes en ese periodo: emitidos y todavía dentro de su ventana de validez. */
    val activeQr: Int,
    /** Entraron y no habían salido al cerrar el periodo. */
    val pending: Int,
    val avgDwellMinutes: Long?
)

data class ReportSummary(
    val period: ReportPeriod,
    val buckets: List<ReportBucket>,
    val totalEntries: Int,
    val totalExits: Int,
    val totalIssued: Int,
    val currentlyInside: Int,
    val avgDwellMinutes: Long?,
    val maxBucketValue: Int
)

/**
 * Agrega los pases en cubetas de tiempo. Se calcula en memoria y no en SQL porque
 * los cortes de día, semana y mes dependen de la zona horaria local del equipo.
 */
object ReportsBuilder {

    const val DAYS = 14
    const val WEEKS = 8
    const val MONTHS = 12

    fun rangeFor(period: ReportPeriod, reference: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val today = Formats.localDate(reference)
        val start = when (period) {
            ReportPeriod.DIA -> today.minusDays((DAYS - 1).toLong())
            ReportPeriod.SEMANA -> today.minusWeeks((WEEKS - 1).toLong()).with(java.time.DayOfWeek.MONDAY)
            ReportPeriod.MES -> today.minusMonths((MONTHS - 1).toLong()).withDayOfMonth(1)
        }
        return Formats.startOfDayFor(start) to (Formats.startOfDayFor(today.plusDays(1)) - 1)
    }

    fun build(
        passes: List<PassEntity>,
        period: ReportPeriod,
        currentlyInside: Int,
        reference: Long = System.currentTimeMillis()
    ): ReportSummary {
        val buckets = bucketsFor(period, reference).map { (start, end, label) ->
            val entriesIn = passes.filter { it.entryAt != null && it.entryAt in start..end }
            val exitsIn = passes.filter { it.exitAt != null && it.exitAt in start..end }
            val issuedIn = passes.count { it.issuedAt in start..end }

            val activeQr = passes.count {
                it.status != PassStatus.REVOCADO && it.validFrom <= end && it.validUntil >= start
            }

            val dwellSamples = exitsIn.mapNotNull { it.dwellMinutes }
            val avgDwell = if (dwellSamples.isEmpty()) null else dwellSamples.average().toLong()

            ReportBucket(
                label = label,
                start = start,
                end = end,
                entries = entriesIn.size,
                exits = exitsIn.size,
                issued = issuedIn,
                activeQr = activeQr,
                pending = (entriesIn.size - exitsIn.size).coerceAtLeast(0),
                avgDwellMinutes = avgDwell
            )
        }

        val allDwell = passes.mapNotNull { it.dwellMinutes }
        return ReportSummary(
            period = period,
            buckets = buckets,
            totalEntries = buckets.sumOf { it.entries },
            totalExits = buckets.sumOf { it.exits },
            totalIssued = buckets.sumOf { it.issued },
            currentlyInside = currentlyInside,
            avgDwellMinutes = if (allDwell.isEmpty()) null else allDwell.average().toLong(),
            maxBucketValue = buckets.maxOfOrNull { maxOf(it.entries, it.exits) } ?: 0
        )
    }

    private fun bucketsFor(period: ReportPeriod, reference: Long): List<Triple<Long, Long, String>> {
        val today = Formats.localDate(reference)
        return when (period) {
            ReportPeriod.DIA -> (0 until DAYS).map { offset ->
                val day = today.minusDays((DAYS - 1 - offset).toLong())
                bucket(day, day.plusDays(1), Formats.dayLabel(Formats.startOfDayFor(day)))
            }

            ReportPeriod.SEMANA -> (0 until WEEKS).map { offset ->
                val monday = today.minusWeeks((WEEKS - 1 - offset).toLong())
                    .with(java.time.DayOfWeek.MONDAY)
                bucket(monday, monday.plusWeeks(1), "Sem ${Formats.date(Formats.startOfDayFor(monday)).substring(0, 5)}")
            }

            ReportPeriod.MES -> (0 until MONTHS).map { offset ->
                val first = today.minusMonths((MONTHS - 1 - offset).toLong()).withDayOfMonth(1)
                bucket(first, first.plusMonths(1), Formats.monthLabel(Formats.startOfDayFor(first)).take(3) + " " + first.year % 100)
            }
        }
    }

    private fun bucket(start: LocalDate, endExclusive: LocalDate, label: String) = Triple(
        Formats.startOfDayFor(start),
        Formats.startOfDayFor(endExclusive) - 1,
        label
    )
}
