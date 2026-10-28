package pl.fleet.monitor.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
    fun toZoned(): ZonedDateTime? = runCatching {
        val zone = runCatching { java.time.ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
        LocalDateTime.of(year, month, day, hour, minute, seconds).atZone(zone)
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
    val lastPositionTime: ZonedDateTime?,
    val mileage: Long?,
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null

    val state: VehicleState
        get() = if (speed > 0 && ignition != Ignition.OFF) VehicleState.DRIVING else VehicleState.PARKED

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
    val begin: ZonedDateTime?,
    val end: ZonedDateTime?,
)

data class HistoryEntry(
    val motion: Boolean,
    val begin: ZonedDateTime?,
    val end: ZonedDateTime?,
    val distanceKm: Double?,
) {
    val durationSeconds: Long?
        get() = if (begin != null && end != null) java.time.Duration.between(begin, end).seconds.coerceAtLeast(0) else null
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
    lastPositionTime = p?.dateTime?.toZoned(),
    mileage = p?.vehicleMileage,
)
