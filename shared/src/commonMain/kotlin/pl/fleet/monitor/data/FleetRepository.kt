package pl.fleet.monitor.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FleetRepository(
    private val apiProvider: () -> EServerApi,
    private val credentials: () -> Credentials?,
) {
    private val api: EServerApi get() = apiProvider()

    private fun creds() = credentials() ?: throw ApiException.Unauthorized()

    suspend fun verify(c: Credentials) {
        api.devices(c)
    }

    suspend fun vehicles(): List<Vehicle> = coroutineScope {
        val c = creds()
        val devices = async { api.devices(c) }
        val positions = async { api.positions(c) }
        mergeVehicles(devices.await(), positions.await())
    }

    suspend fun status(serial: String, at: Instant = now()) = api.status(creds(), normalizeSerial(serial), at)

    suspend fun lastReport(serial: String) = api.lastReadingDate(creds(), normalizeSerial(serial))

    suspend fun history(serial: String, from: Instant, to: Instant, details: Boolean = true) =
        api.history(creds(), normalizeSerial(serial), from, to, details)

    /** Zestaw zapytań diagnostycznych – każda funkcja z datą w trzech formatach. */
    suspend fun diagnostics(rawSerial: String): String {
        val serial = normalizeSerial(rawSerial)
        val c = creds()
        val zone = TimeZone.currentSystemDefault()
        val now = now()
        val today = now.toLocalDateTime(zone).date
        val midnight = today.atStartOfDayIn(zone)
        fun local(t: Instant) = ApiDates.formatDateTime(t.toLocalDateTime(zone))
        fun utc(t: Instant) = ApiDates.formatDateTime(t.toLocalDateTime(TimeZone.UTC))
        fun ts(t: Instant) = t.epochSeconds.toString()
        val formats = listOf("local" to ::local, "utc" to ::utc, "timestamp" to ::ts)
        val out = StringBuilder()
        out.appendLine("Telefon: ${now} (strefa $zone), serial: $serial")
        for ((name, fmt) in formats) {
            out.appendLine("\n=== status, date_format=$name, date=${fmt((now - 2.minutes))}")
            out.appendLine(api.raw(c, "status", mapOf("serial" to serial, "date" to fmt((now - 2.minutes)), "date_format" to name)))
            out.appendLine("\n=== history, date_format=$name, ${fmt(midnight)} – ${fmt(now)}")
            out.appendLine(api.raw(c, "history", mapOf(
                "serial" to serial, "date_from" to fmt(midnight), "date_to" to fmt(now), "details" to "1", "date_format" to name,
            )).take(1200))
            out.appendLine("\n=== last_reading_date, date_format=$name")
            out.appendLine(api.raw(c, "last_reading_date", mapOf("serial" to serial, "date_format" to name)))
        }
        out.appendLine("\n=== history bez date_format, $today")
        out.appendLine(api.raw(c, "history", mapOf(
            "serial" to serial, "date_from" to today.toString(), "date_to" to (midnight + 1.days).toLocalDateTime(zone).date.toString(), "details" to "1",
        )).take(1200))
        return out.toString()
    }
}
