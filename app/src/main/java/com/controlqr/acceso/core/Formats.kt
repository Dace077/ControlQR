package com.controlqr.acceso.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Formatos y cortes de calendario en hora local. Los reportes se agrupan en la zona
 * del dispositivo, no en UTC: "cuántos entraron el lunes" tiene que coincidir con lo
 * que vio el vigilante, no con un cambio de día a media tarde.
 */
object Formats {

    private val locale = Locale("es", "MX")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayLabelFmt = DateTimeFormatter.ofPattern("EEE dd MMM", locale)
    private val monthLabelFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val fileFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", locale)

    fun dateTime(millis: Long?): String =
        millis?.let { dateTimeFmt.format(local(it)) } ?: "—"

    fun date(millis: Long?): String =
        millis?.let { dateFmt.format(local(it)) } ?: "—"

    fun time(millis: Long?): String =
        millis?.let { timeFmt.format(local(it)) } ?: "—"

    fun dayLabel(millis: Long): String =
        dayLabelFmt.format(local(millis)).replaceFirstChar { it.uppercase() }

    fun monthLabel(millis: Long): String =
        monthLabelFmt.format(local(millis)).replaceFirstChar { it.uppercase() }

    fun fileStamp(millis: Long = System.currentTimeMillis()): String = fileFmt.format(local(millis))

    /** "2 h 35 min" — cómo se lee el tiempo de permanencia en los reportes. */
    fun duration(minutes: Long?): String {
        if (minutes == null) return "—"
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) "$hours h" else "$hours h $rest min"
    }

    fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    // ----------------------------------------------------------- cortes de tiempo

    fun startOfDay(millis: Long = System.currentTimeMillis()): Long =
        millisOf(local(millis).toLocalDate().atStartOfDay())

    fun endOfDay(millis: Long = System.currentTimeMillis()): Long =
        startOfDay(millis) + 24 * 60 * 60_000L - 1

    fun startOfWeek(millis: Long = System.currentTimeMillis()): Long =
        millisOf(
            local(millis).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay()
        )

    fun startOfMonth(millis: Long = System.currentTimeMillis()): Long =
        millisOf(local(millis).toLocalDate().withDayOfMonth(1).atStartOfDay())

    fun startOfDayFor(date: LocalDate): Long = millisOf(date.atStartOfDay())

    fun localDate(millis: Long): LocalDate = local(millis).toLocalDate()
}
