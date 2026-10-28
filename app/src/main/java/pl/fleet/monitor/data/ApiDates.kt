package pl.fleet.monitor.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Daty wysyłane do API i odbierane z API.
 * Używamy formatu `utc` – jest jednoznaczny niezależnie od strefy czasowej ustawionej na koncie;
 * konwersję na czas lokalny telefonu robi aplikacja.
 */
object ApiDates {
    const val FORMAT = "utc"

    private val apiFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun format(time: ZonedDateTime): String =
        time.withZoneSameInstant(ZoneOffset.UTC).format(apiFormatter)

    /** Przyjmuje timestamp (sekundy lub milisekundy), "yyyy-MM-dd HH:mm:ss", ISO-8601 lub samą datę. */
    fun parse(raw: String?): ZonedDateTime? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        s.toLongOrNull()?.let { n ->
            val instant = if (n > 100_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
            return instant.atZone(ZoneOffset.UTC)
        }
        return runCatching { LocalDateTime.parse(s, apiFormatter).atZone(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(s) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).atZone(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC) }.getOrNull()
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
