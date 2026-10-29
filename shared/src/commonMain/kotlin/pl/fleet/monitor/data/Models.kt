package pl.fleet.monitor.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ---- Odpowiedzi API (DTO) ----

@Serializable
data class DeviceListResponse(
    val deviceList: List<DeviceDto> = emptyList(),
)

@Serializable
data class DeviceDto(
    val deviceId: String,
    val deviceName: String? = null,
    val serial: String? = null,
    val plateNo: String? = null,
)

@Serializable
data class PositionListResponse(
    val positionList: List<PositionDto> = emptyList(),
)

@Serializable
data class PositionDto(
    val deviceId: String,
    val coordinate: CoordinateDto? = null,
    val heading: Int? = null,
    val speed: Int? = null,
    val ignitionState: String? = null,
    val dateTime: DateTimeDto? = null,
    val serial: String? = null,
    val vehicleMileage: Long? = null,
)

@Serializable
data class CoordinateDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class DateTimeDto(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 0,
    val minute: Int = 0,
    val seconds: Int = 0,
    val timezone: String? = "UTC",
) {
    /** API zwraca obecnie zawsze czas w UTC. */
    fun toInstant(): Instant? = runCatching {
        val zone = runCatching { TimeZone.of(timezone ?: "UTC") }.getOrDefault(TimeZone.UTC)
        LocalDateTime(year, month, day, hour, minute, seconds).toInstant(zone)
    }.getOrNull()
}

// ---- Modele domenowe ----

enum class Ignition { ON, OFF, UNKNOWN;
    companion object {
        fun parse(raw: String?): Ignition = when (raw?.uppercase()) {
            "ON" -> ON
            "OFF" -> OFF
            else -> UNKNOWN
        }
    }
}

/** Stan pojazdu: jedzie albo stoi (każdy pojazd, który nie jedzie, jest zaparkowany). */
enum class VehicleState { DRIVING, PARKED }

data class Vehicle(
    val deviceId: String,
    val name: String,
    val plateNo: String,
    val serial: String,
    val latitude: Double?,
    val longitude: Double?,
    val heading: Int,
    val speed: Int,
    val ignition: Ignition,
    val lastPositionTime: Instant?,
    val mileage: Long?,
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null

    val state: VehicleState
        // Tylko prędkość: część nadajników wybudza się z akcelerometru i zawsze raportuje zapłon wyłączony.
        get() = if (speed > 0) VehicleState.DRIVING else VehicleState.PARKED

    /** Etykieta do wyświetlania – numer rejestracyjny, a gdy go brak – nazwa. */
    val label: String get() = plateNo.ifBlank { displayName.ifBlank { serial } }

    /** Nazwa pojazdu bez numeru nadajnika GPS (API skleja go w deviceName: „model marka nr nr_rej.”). */
    val displayName: String
        get() {
            // Usuwamy numer w obu postaciach: z zerami wiodącymi i bez nich.
            val cleaned = if (serial.isBlank()) name
            else name.replace(serial, " ").replace(serial.trimStart('0').ifEmpty { serial }, " ")
            return cleaned.replace(Regex("\\s+"), " ").trim().ifBlank { plateNo }
        }
}

/** Wynik funkcji [status] – aktywność pojazdu w danym momencie. */
data class MotionStatus(
    val motion: Boolean,
    val begin: Instant?,
    val end: Instant?,
)

data class HistoryEntry(
    val motion: Boolean,
    val begin: Instant?,
    val end: Instant?,
    val distanceKm: Double?,
) {
    val durationSeconds: Long?
        get() = if (begin != null && end != null) (end - begin).inWholeSeconds.coerceAtLeast(0) else null
}

data class HistorySummary(
    val distanceKm: Double,
    val drivingSeconds: Long,
    val parkingSeconds: Long,
    val entries: List<HistoryEntry>,
)

/** Długość numeru nadajnika GPS. */
const val SERIAL_LENGTH = 8

/**
 * Numer nadajnika ma zawsze 8 cyfr, ale API gubi zera wiodące (np. „2000130” zamiast „02000130”).
 * Uzupełniamy go zerami – zarówno do wyświetlania, jak i do zapytań status / history / last_reading_date.
 */
fun normalizeSerial(serial: String): String {
    val s = serial.trim()
    return if (s.isNotEmpty() && s.length < SERIAL_LENGTH && s.all { it.isDigit() }) s.padStart(SERIAL_LENGTH, '0') else s
}

fun mergeVehicles(devices: List<DeviceDto>, positions: List<PositionDto>): List<Vehicle> {
    val posById = positions.associateBy { it.deviceId }
    val known = devices.map { d ->
        val p = posById[d.deviceId]
        buildVehicle(d.deviceId, d.deviceName, d.plateNo, d.serial ?: p?.serial, p)
    }
    // Pozycje pojazdów, których nie ma na liście urządzeń (nie powinno się zdarzać, ale nie gubimy danych).
    val knownIds = devices.map { it.deviceId }.toSet()
    val extra = positions.filter { it.deviceId !in knownIds }
        .map { buildVehicle(it.deviceId, null, null, it.serial, it) }
    return (known + extra).sortedBy { it.label.lowercase() }
}

private fun buildVehicle(id: String, name: String?, plate: String?, serial: String?, p: PositionDto?) = Vehicle(
    deviceId = id,
    name = name.orEmpty(),
    plateNo = plate.orEmpty(),
    serial = normalizeSerial(serial.orEmpty()),
    latitude = p?.coordinate?.latitude,
    longitude = p?.coordinate?.longitude,
    heading = p?.heading ?: 0,
    speed = p?.speed ?: 0,
    ignition = Ignition.parse(p?.ignitionState),
    lastPositionTime = p?.dateTime?.toInstant(),
    mileage = p?.vehicleMileage,
)
