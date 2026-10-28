package pl.fleet.monitor.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.FleetApp
import pl.fleet.monitor.data.HistoryEntry
import pl.fleet.monitor.data.HistorySummary
import pl.fleet.monitor.ui.Fmt
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RangePreset(val label: String) { TODAY("Dziś"), YESTERDAY("Wczoraj"), WEEK("7 dni"), MONTH("30 dni"), CUSTOM("Zakres…") }

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
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
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
                .groupBy { e -> e.begin?.let { Fmt.local(it).toLocalDate() } ?: from }
                .toSortedMap(compareByDescending { it })
                .map { (date, list) -> DayGroup(date, list.sortedByDescending { it.begin }) }
        }
}

class HistoryViewModel(app: Application, private val serial: String) : AndroidViewModel(app) {
    private val repo = (app as FleetApp).repository

    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun selectPreset(p: RangePreset) {
        val today = LocalDate.now()
        val (from, to) = when (p) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            RangePreset.WEEK -> today.minusDays(6) to today
            RangePreset.MONTH -> today.minusDays(29) to today
            RangePreset.CUSTOM -> return
        }
        _ui.update { it.copy(preset = p, from = from, to = to) }
        load()
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) {
        _ui.update { it.copy(preset = RangePreset.CUSTOM, from = minOf(from, to), to = maxOf(from, to)) }
        load()
    }

    fun toggleOnlyTrips() = _ui.update { it.copy(onlyTrips = !it.onlyTrips) }

    fun load() {
        job?.cancel()
        val s = _ui.value
        val zone = ZoneId.systemDefault()
        val start = s.from.atStartOfDay(zone)
        val endOfDay = s.to.plusDays(1).atStartOfDay(zone).minusSeconds(1)
        val end = minOf(endOfDay, ZonedDateTime.now(zone))
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
                e.distanceKm?.let { String.format(java.util.Locale.forLanguageTag("pl-PL"), "%.1f", it) }.orEmpty(),
            ).joinToString(";"))
        }
    }
}
