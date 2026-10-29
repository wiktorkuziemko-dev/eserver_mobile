package pl.fleet.monitor.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Bieżący moment – w jednym miejscu, żeby łatwo podmienić w testach. */
fun now(): Instant = Clock.System.now()

/**
 * Daty wysyłane do API i odbierane z API.
 * Używamy formatu `local` (czas w strefie użytkownika) – tak jak we wszystkich działających
 * przykładach z dokumentacji. Zakładamy, że strefa konta jest taka sama jak strefa telefonu.
 */
object ApiDates {
    const val FORMAT = "local"

    /** Strefa, w której serwer podaje i przyjmuje daty w formacie `local`. */
    var zone: TimeZone = TimeZone.currentSystemDefault()

    fun format(time: Instant): String = formatDateTime(time.toLocalDateTime(zone))

    /** „yyyy-MM-dd HH:mm:ss”. */
    fun formatDateTime(l: LocalDateTime): String =
        "${l.year}-${pad2(l.month.number)}-${pad2(l.day)} ${pad2(l.hour)}:${pad2(l.minute)}:${pad2(l.second)}"

    /** Przyjmuje timestamp (sekundy lub milisekundy), "yyyy-MM-dd HH:mm[:ss]", ISO-8601 lub samą datę. */
    fun parse(raw: String?): Instant? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        s.toLongOrNull()?.let { n ->
            return if (n > 100_000_000_000L) Instant.fromEpochMilliseconds(n) else Instant.fromEpochSeconds(n)
        }
        return runCatching { LocalDateTime.parse(s.replace(' ', 'T')).toInstant(zone) }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { LocalDate.parse(s).atStartOfDayIn(zone) }.getOrNull()
    }

    fun parse(element: JsonElement?): Instant? =
        (element as? JsonPrimitive)?.contentOrNull?.let(::parse)
}

internal fun pad2(n: Int): String = n.toString().padStart(2, '0')

internal fun JsonElement?.asBoolean(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    p.booleanOrNull?.let { return it }
    p.longOrNull?.let { return it != 0L }
    return p.contentOrNull?.lowercase() in setOf("true", "yes", "on", "1")
}

internal fun JsonElement?.asDouble(): Double? =
    (this as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()

internal fun JsonElement?.asLong(): Long? = asDouble()?.toLong()
