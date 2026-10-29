package pl.fleet.monitor.ui

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import pl.fleet.monitor.data.Ignition
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.data.now
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Formatowanie dat, czasów i liczb po polsku (w strefie czasowej telefonu). */
object Fmt {
    private val DAYS = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")
    private val MONTHS_GENITIVE = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )

    val zone: TimeZone get() = TimeZone.currentSystemDefault()

    fun local(t: Instant): LocalDateTime = t.toLocalDateTime(zone)
    fun today(): LocalDate = now().toLocalDateTime(zone).date

    private fun hm(l: LocalDateTime) = "${p2(l.hour)}:${p2(l.minute)}"
    private fun hms(l: LocalDateTime) = "${hm(l)}:${p2(l.second)}"
    private fun dm(d: LocalDate) = "${p2(d.day)}.${p2(d.month.number)}"
    private fun dmy(d: LocalDate) = "${dm(d)}.${d.year}"

    fun time(t: Instant?) = t?.let { hm(local(it)) } ?: "—"
    fun timeSec(t: Instant?) = t?.let { hms(local(it)) } ?: "—"
    fun date(t: Instant?) = t?.let { dmy(local(it).date) } ?: "—"
    fun dateTime(t: Instant?) = t?.let { local(it).let { l -> "${dmy(l.date)} ${hm(l)}" } } ?: "—"
    fun date(d: LocalDate) = dmy(d)
    fun dayMonth(d: LocalDate) = dm(d)

    /** „Poniedziałek, 28 października 2026”. */
    fun dayHeader(d: LocalDate): String =
        "${DAYS[d.dayOfWeek.isoDayNumber - 1]}, ${d.day} ${MONTHS_GENITIVE[d.month.number - 1]} ${d.year}"
            .replaceFirstChar { it.uppercaseChar() }

    /** Godzina z datą, jeśli zdarzenie nie miało miejsca dziś. */
    fun smartTime(t: Instant?): String {
        if (t == null) return "—"
        val l = local(t)
        return if (l.date == today()) hm(l) else "${dm(l.date)} ${hm(l)}"
    }

    /** „Dzisiaj, 16:32:22”, „Wczoraj, 08:05:10” albo „27.10.2026, 16:32:22”. */
    fun stamp(t: Instant?): String {
        if (t == null) return "—"
        val l = local(t)
        val day = when (l.date) {
            today() -> "Dzisiaj"
            today().minus(1, DateTimeUnit.DAY) -> "Wczoraj"
            else -> dmy(l.date)
        }
        return "$day, ${hms(l)}"
    }

    /** Krótko: „16:32” dla dzisiejszej daty, „27.10 16:32” dla wcześniejszej. */
    fun shortStamp(t: Instant?): String = smartTime(t)

    fun ago(t: Instant?, at: Instant = now()): String {
        if (t == null) return "brak danych"
        val s = (at - t).inWholeSeconds
        return when {
            s < 60 -> "przed chwilą"
            s < 3600 -> "${s / 60} min temu"
            s < 86400 -> "${s / 3600} h ${(s % 3600) / 60} min temu"
            else -> "${s / 86400} dni temu"
        }
    }

    fun isStale(t: Instant?, hours: Long = 2): Boolean =
        t == null || (now() - t).inWholeHours >= hours

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

    fun km(value: Double?): String = value?.let { decimal(it, 1) + " km" } ?: "—"

    fun mileage(value: Long?): String = value?.let { grouped(it) + " km" } ?: "—"

    fun speed(v: Int) = "$v km/h"

    fun coords(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "—" else "${fixed(lat, 5, '.')}, ${fixed(lon, 5, '.')}"

    /** Liczba z [decimals] miejscami po przecinku i separatorem tysięcy (zapis polski). */
    fun decimal(value: Double, decimals: Int): String {
        val txt = fixed(value, decimals, ',')
        val sign = if (txt.startsWith("-")) "-" else ""
        val body = txt.removePrefix("-")
        val intPart = body.substringBefore(',')
        val frac = body.substringAfter(',', "")
        return sign + grouped(intPart.toLong()) + if (frac.isNotEmpty()) ",$frac" else ""
    }

    /** Separator tysięcy: twarda spacja, jak w polskim formacie liczb. */
    fun grouped(value: Long): String {
        val digits = abs(value).toString()
        val out = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) out.append('\u00A0')
            out.append(c)
        }
        return (if (value < 0) "-" else "") + out
    }

    private fun fixed(value: Double, decimals: Int, separator: Char): String {
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = (abs(value) * factor).roundToLong()
        val intPart = scaled / factor
        val frac = (scaled % factor).toString().padStart(decimals, '0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (decimals == 0) "$sign$intPart" else "$sign$intPart$separator$frac"
    }

    private fun p2(n: Int) = n.toString().padStart(2, '0')

    fun ignition(i: Ignition) = when (i) {
        Ignition.ON -> "Włączony"
        Ignition.OFF -> "Wyłączony"
        Ignition.UNKNOWN -> "Nieznany"
    }
}

val VehicleState.label: String
    get() = when (this) {
        VehicleState.DRIVING -> "W ruchu"
        VehicleState.PARKED -> "Zaparkowany"
    }

val VehicleState.shortLabel: String
    get() = when (this) {
        VehicleState.DRIVING -> "W ruchu"
        VehicleState.PARKED -> "Zaparkowany"
    }

val VehicleState.color: Color
    get() = when (this) {
        VehicleState.DRIVING -> Color(0xFF2E9E44)
        VehicleState.PARKED -> Color(0xFFF59300)
    }
