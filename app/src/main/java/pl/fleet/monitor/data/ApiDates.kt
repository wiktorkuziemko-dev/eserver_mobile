package pl.fleet.monitor.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Daty wysyłane do API i odbierane z API.
 * Używamy formatu `local` (czas w strefie użytkownika) – tak jak we wszystkich działających
 * przykładach z dokumentacji. Zakładamy, że strefa konta jest taka sama jak strefa telefonu.
 */
object ApiDates {
    const val FORMAT = "local"

    /** Strefa, w której serwer podaje i przyjmuje daty w formacie `local`. */
    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()

    private val apiFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun format(time: ZonedDateTime): String =
        time.withZoneSameInstant(zone).format(apiFormatter)

    /** Przyjmuje timestamp (sekundy lub milisekundy), "yyyy-MM-dd HH:mm:ss", ISO-8601 lub samą datę. */
    fun parse(raw: String?): ZonedDateTime? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        s.toLongOrNull()?.let { n ->
            val instant = if (n > 100_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
            return instant.atZone(ZoneOffset.UTC)
        }
        return runCatching { LocalDateTime.parse(s, apiFormatter).atZone(zone) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s, shortFormatter).atZone(zone) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(s) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).atZone(zone) }.getOrNull()
            ?: runCatching { LocalDate.parse(s).atStartOfDay(zone) }.getOrNull()
    }

    fun parse(element: JsonElement?): ZonedDateTime? =
        (element as? JsonPrimitive)?.contentOrNull?.let(::parse)
}

internal fun JsonElement?.asBoolean(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    p.booleanOrNull?.let { return it }
    p.longOrNull?.let { return it != 0L }
    return p.contentOrNull?.lowercase() in setOf("true", "yes", "on", "1")
}

internal fun JsonElement?.asDouble(): Double? =
    (this as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()

internal fun JsonElement?.asLong(): Long? = asDouble()?.toLong()
