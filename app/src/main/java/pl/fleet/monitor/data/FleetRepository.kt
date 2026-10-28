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

    suspend fun status(serial: String, at: ZonedDateTime = ZonedDateTime.now()): MotionStatus =
        withSerialFallback(serial, isEmpty = { false }) { api.status(creds(), it, at) }

    suspend fun lastReport(serial: String): ZonedDateTime? =
        withSerialFallback(serial, isEmpty = { it == null }) { api.lastReadingDate(creds(), it) }

    suspend fun history(serial: String, from: ZonedDateTime, to: ZonedDateTime, details: Boolean = true): HistorySummary =
        withSerialFallback(serial, isEmpty = { it.entries.isEmpty() && it.distanceKm == 0.0 && it.drivingSeconds == 0L }) {
            api.history(creds(), it, from, to, details)
        }

    /** Numer nadajnika, który zadziałał dla funkcji z datami (np. bez zer wiodących). */
    private val workingSerial = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Lista pojazdów podaje numery nadajników z zerami wiodącymi (np. „0123456”), a funkcje
     * status / history / last_reading_date znajdują dane tylko pod numerem bez nich („123456”).
     * Gdy zapytanie z oryginalnym numerem nie zwróci danych, ponawiamy je z numerem bez zer
     * i zapamiętujemy działający wariant.
     */
    private suspend fun <T> withSerialFallback(serial: String, isEmpty: (T) -> Boolean, call: suspend (String) -> T): T {
        workingSerial[serial]?.let { return call(it) }
        val alternative = serial.trimStart('0')
        if (alternative == serial || alternative.isEmpty()) return call(serial)

        val first = try {
            call(serial)
        } catch (e: ApiException.Server) {
            return call(alternative).also { workingSerial[serial] = alternative }
        }
        if (!isEmpty(first)) return first.also { workingSerial[serial] = serial }
        val second = runCatching { call(alternative) }.getOrNull() ?: return first
        return if (isEmpty(second)) first else second.also { workingSerial[serial] = alternative }
    }

    /** Zestaw zapytań diagnostycznych – każda funkcja z datą w trzech formatach. */
    suspend fun diagnostics(serial: String): String {
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
