package pl.fleet.monitor.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime

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

    suspend fun status(serial: String, at: ZonedDateTime = ZonedDateTime.now()) = api.status(creds(), normalizeSerial(serial), at)

    suspend fun lastReport(serial: String) = api.lastReadingDate(creds(), normalizeSerial(serial))

    suspend fun history(serial: String, from: ZonedDateTime, to: ZonedDateTime, details: Boolean = true) =
        api.history(creds(), normalizeSerial(serial), from, to, details)

    /** Zestaw zapytań diagnostycznych – każda funkcja z datą w trzech formatach. */
    suspend fun diagnostics(rawSerial: String): String {
        val serial = normalizeSerial(rawSerial)
        val c = creds()
        val zone = java.time.ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val midnight = now.toLocalDate().atStartOfDay(zone)
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        fun local(t: ZonedDateTime) = t.withZoneSameInstant(zone).format(f)
        fun utc(t: ZonedDateTime) = t.withZoneSameInstant(java.time.ZoneOffset.UTC).format(f)
        fun ts(t: ZonedDateTime) = t.toEpochSecond().toString()
        val formats = listOf("local" to ::local, "utc" to ::utc, "timestamp" to ::ts)
        val out = StringBuilder()
        out.appendLine("Telefon: ${now} (strefa $zone), serial: $serial")
        for ((name, fmt) in formats) {
            out.appendLine("\n=== status, date_format=$name, date=${fmt(now.minusMinutes(2))}")
            out.appendLine(api.raw(c, "status", mapOf("serial" to serial, "date" to fmt(now.minusMinutes(2)), "date_format" to name)))
            out.appendLine("\n=== history, date_format=$name, ${fmt(midnight)} – ${fmt(now)}")
            out.appendLine(api.raw(c, "history", mapOf(
                "serial" to serial, "date_from" to fmt(midnight), "date_to" to fmt(now), "details" to "1", "date_format" to name,
            )).take(1200))
            out.appendLine("\n=== last_reading_date, date_format=$name")
            out.appendLine(api.raw(c, "last_reading_date", mapOf("serial" to serial, "date_format" to name)))
        }
        out.appendLine("\n=== history bez date_format, ${now.toLocalDate()}")
        out.appendLine(api.raw(c, "history", mapOf(
            "serial" to serial, "date_from" to now.toLocalDate().toString(), "date_to" to now.toLocalDate().plusDays(1).toString(), "details" to "1",
        )).take(1200))
        return out.toString()
    }
}
