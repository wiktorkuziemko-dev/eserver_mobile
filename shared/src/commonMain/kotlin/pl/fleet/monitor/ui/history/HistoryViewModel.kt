package pl.fleet.monitor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.now
import kotlin.time.Duration.Companion.seconds
import pl.fleet.monitor.data.HistoryEntry
import pl.fleet.monitor.data.HistorySummary
import pl.fleet.monitor.ui.Fmt

/** Maksymalna długość okresu historii (w dniach). */
const val MAX_DAYS = 14

enum class RangePreset(val label: String) { TODAY("Dziś"), YESTERDAY("Wczoraj"), WEEK("7 dni"), TWO_WEEKS("14 dni"), CUSTOM("Zakres…") }

data class DayGroup(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
) {
    val distanceKm: Double get() = entries.filter { it.motion }.sumOf { it.distanceKm ?: 0.0 }
    val drivingSeconds: Long get() = entries.filter { it.motion }.sumOf { it.durationSeconds ?: 0 }
    val trips: Int get() = entries.count { it.motion }
}

data class HistoryUiState(
    val preset: RangePreset = RangePreset.TODAY,
    val from: LocalDate = Fmt.today(),
    val to: LocalDate = Fmt.today(),
    val onlyTrips: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val summary: HistorySummary? = null,
) {
    val tripsCount: Int get() = summary?.entries?.count { it.motion } ?: 0

    val days: List<DayGroup>
        get() {
            val entries = summary?.entries.orEmpty().filter { !onlyTrips || it.motion }
            return entries
                .groupBy { e -> e.begin?.let { Fmt.local(it).date } ?: from }
                .entries.sortedByDescending { it.key }
                .map { (date, list) -> DayGroup(date, list.sortedByDescending { it.begin }) }
        }
}

class HistoryViewModel(private val serial: String) : ViewModel() {
    private val repo = AppGraph.container.repository

    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun selectPreset(p: RangePreset) {
        val today = Fmt.today()
        val (from, to) = when (p) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minus(1, DateTimeUnit.DAY) to today.minus(1, DateTimeUnit.DAY)
            RangePreset.WEEK -> today.minus(6, DateTimeUnit.DAY) to today
            RangePreset.TWO_WEEKS -> today.minus(MAX_DAYS - 1, DateTimeUnit.DAY) to today
            RangePreset.CUSTOM -> return
        }
        _ui.update { it.copy(preset = p, from = from, to = to) }
        load()
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) {
        val end = maxOf(from, to)
        // Serwer prezentuje dane maksymalnie z 14 dni – dłuższy zakres przycinamy.
        val start = maxOf(minOf(from, to), end.minus(MAX_DAYS - 1, DateTimeUnit.DAY))
        _ui.update { it.copy(preset = RangePreset.CUSTOM, from = start, to = end) }
        load()
    }

    fun toggleOnlyTrips() = _ui.update { it.copy(onlyTrips = !it.onlyTrips) }

    fun load() {
        job?.cancel()
        if (serial.isBlank()) {
            _ui.update { it.copy(loading = false, error = "Brak numeru urządzenia GPS dla tego pojazdu.") }
            return
        }
        val s = _ui.value
        val zone = Fmt.zone
        val start = s.from.atStartOfDayIn(zone)
        val endOfDay = s.to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.seconds
        val end = minOf(endOfDay, now())
        _ui.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val result = repo.history(serial, start, end, details = true)
                _ui.update { it.copy(loading = false, summary = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Błąd", summary = null) }
            }
        }
    }

    /** Historia w formacie CSV (separator ';' – poprawnie otwiera się w polskim Excelu). */
    fun csv(vehicleLabel: String): String = buildString {
        appendLine("Pojazd;$vehicleLabel")
        appendLine("Okres;${Fmt.date(_ui.value.from)} - ${Fmt.date(_ui.value.to)}")
        appendLine("Typ;Początek;Koniec;Czas trwania;Dystans [km]")
        _ui.value.summary?.entries.orEmpty().forEach { e ->
            appendLine(listOf(
                if (e.motion) "Jazda" else "Postój",
                Fmt.dateTime(e.begin),
                Fmt.dateTime(e.end),
                Fmt.duration(e.durationSeconds),
                e.distanceKm?.let { Fmt.decimal(it, 1).replace("\u00A0", "") }.orEmpty(),
            ).joinToString(";"))
        }
    }
}
