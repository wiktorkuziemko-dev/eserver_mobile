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

    suspend fun status(serial: String, at: ZonedDateTime = ZonedDateTime.now()) = api.status(creds(), serial, at)

    suspend fun lastReport(serial: String) = api.lastReadingDate(creds(), serial)

    suspend fun history(serial: String, from: ZonedDateTime, to: ZonedDateTime, details: Boolean = true) =
        api.history(creds(), serial, from, to, details)
}
