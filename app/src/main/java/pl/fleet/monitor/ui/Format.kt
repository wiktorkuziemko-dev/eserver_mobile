package pl.fleet.monitor.ui

import androidx.compose.ui.graphics.Color
import pl.fleet.monitor.data.Ignition
import pl.fleet.monitor.data.VehicleState
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val PL = Locale.forLanguageTag("pl-PL")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", PL)
    private val timeSecFmt = DateTimeFormatter.ofPattern("HH:mm:ss", PL)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", PL)
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", PL)
    private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", PL)
    private val shortDayFmt = DateTimeFormatter.ofPattern("dd.MM", PL)

    fun local(t: ZonedDateTime): ZonedDateTime = t.withZoneSameInstant(ZoneId.systemDefault())

    fun time(t: ZonedDateTime?) = t?.let { local(it).format(timeFmt) } ?: "—"
    fun timeSec(t: ZonedDateTime?) = t?.let { local(it).format(timeSecFmt) } ?: "—"
    fun date(t: ZonedDateTime?) = t?.let { local(it).format(dateFmt) } ?: "—"
    fun dateTime(t: ZonedDateTime?) = t?.let { local(it).format(dateTimeFmt) } ?: "—"
    fun date(d: LocalDate) = d.format(dateFmt)
    fun dayHeader(d: LocalDate) = d.format(dayHeaderFmt).replaceFirstChar { it.titlecase(PL) }

    /** Godzina z datą, jeśli zdarzenie nie miało miejsca dziś. */
    fun smartTime(t: ZonedDateTime?): String {
        if (t == null) return "—"
        val l = local(t)
        return if (l.toLocalDate() == LocalDate.now()) l.format(timeFmt) else l.format(shortDayFmt) + " " + l.format(timeFmt)
    }

    fun ago(t: ZonedDateTime?, now: ZonedDateTime = ZonedDateTime.now()): String {
        if (t == null) return "brak danych"
        val s = Duration.between(t, now).seconds
        return when {
            s < 60 -> "przed chwilą"
            s < 3600 -> "${s / 60} min temu"
            s < 86400 -> "${s / 3600} h ${(s % 3600) / 60} min temu"
            else -> "${s / 86400} dni temu"
        }
    }

    fun isStale(t: ZonedDateTime?, hours: Long = 2): Boolean =
        t == null || Duration.between(t, ZonedDateTime.now()).toHours() >= hours

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d} d ${h} h ${m} min"
            h > 0 -> "${h} h ${m} min"
            m > 0 -> "${m} min"
            else -> "${seconds} s"
        }
    }

    fun km(value: Double?): String {
        if (value == null) return "—"
        val nf = NumberFormat.getNumberInstance(PL).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
        return nf.format(value) + " km"
    }

    fun mileage(value: Long?): String =
        value?.let { NumberFormat.getIntegerInstance(PL).format(it) + " km" } ?: "—"

    fun speed(v: Int) = "$v km/h"

    fun heading(deg: Int): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((deg % 360) + 360) % 360 + 22) / 45 % 8
        return "${names[idx]} ($deg°)"
    }

    fun coords(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "—" else String.format(Locale.US, "%.5f, %.5f", lat, lon)

    fun ignition(i: Ignition) = when (i) {
        Ignition.ON -> "Włączony"
        Ignition.OFF -> "Wyłączony"
        Ignition.UNKNOWN -> "Nieznany"
    }
}

val VehicleState.label: String
    get() = when (this) {
        VehicleState.DRIVING -> "W trasie"
        VehicleState.IDLE -> "Postój, zapłon wł."
        VehicleState.PARKED -> "Postój"
        VehicleState.UNKNOWN -> "Brak danych"
    }

val VehicleState.shortLabel: String
    get() = when (this) {
        VehicleState.DRIVING -> "Jazda"
        VehicleState.IDLE -> "Zapłon wł."
        VehicleState.PARKED -> "Postój"
        VehicleState.UNKNOWN -> "Brak danych"
    }

val VehicleState.color: Color
    get() = when (this) {
        VehicleState.DRIVING -> Color(0xFF2E7D32)
        VehicleState.IDLE -> Color(0xFFEF8F00)
        VehicleState.PARKED -> Color(0xFF1565C0)
        VehicleState.UNKNOWN -> Color(0xFF8E8E8E)
    }
