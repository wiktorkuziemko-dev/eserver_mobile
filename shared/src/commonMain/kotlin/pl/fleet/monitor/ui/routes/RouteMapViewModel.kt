package pl.fleet.monitor.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.RouteAnalysis
import pl.fleet.monitor.data.analyzeRoute
import pl.fleet.monitor.data.now
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.history.RangePreset
import kotlin.time.Duration.Companion.seconds

/** Maksymalna długość okresu tras na mapie (w dniach). */
const val MAX_ROUTE_DAYS = 7

/** Zakresy dostępne na mapie tras (bez 14 dni). */
val ROUTE_PRESETS = listOf(RangePreset.TODAY, RangePreset.YESTERDAY, RangePreset.WEEK)

data class RouteMapUiState(
    val preset: RangePreset = RangePreset.TODAY,
    val from: LocalDate = Fmt.today(),
    val to: LocalDate = Fmt.today(),
    val loading: Boolean = false,
    val error: String? = null,
    val analysis: RouteAnalysis? = null,
    val selectedTrip: Int? = null,
    val selectedStop: Int? = null,
)

class RouteMapViewModel(private val serial: String) : ViewModel() {
    private val repo = AppGraph.container.repository

    private val _ui = MutableStateFlow(RouteMapUiState())
    val ui: StateFlow<RouteMapUiState> = _ui.asStateFlow()

    val mapState = RouteMapState()
    private var job: Job? = null

    init { load() }

    fun selectPreset(p: RangePreset) {
        val today = Fmt.today()
        val (from, to) = when (p) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minus(1, DateTimeUnit.DAY) to today.minus(1, DateTimeUnit.DAY)
            RangePreset.WEEK -> today.minus(MAX_ROUTE_DAYS - 1, DateTimeUnit.DAY) to today
            else -> return
        }
        _ui.update { it.copy(preset = p, from = from, to = to) }
        load()
    }

    fun setCustomRange(from: LocalDate, to: LocalDate) {
        val end = maxOf(from, to)
        val start = maxOf(minOf(from, to), end.minus(MAX_ROUTE_DAYS - 1, DateTimeUnit.DAY))
        _ui.update { it.copy(preset = RangePreset.CUSTOM, from = start, to = end) }
        load()
    }

    fun selectTrip(i: Int?) = _ui.update { it.copy(selectedTrip = i, selectedStop = null) }

    fun selectStop(i: Int?) {
        _ui.update { it.copy(selectedStop = i, selectedTrip = if (i != null) null else it.selectedTrip) }
        i?.let { _ui.value.analysis?.stops?.getOrNull(it) }?.let(mapState::focusStop)
    }

    /** Poprzednia / następna trasa w kolejności czasu. */
    fun stepTrip(delta: Int) {
        val count = _ui.value.analysis?.trips?.size ?: return
        if (count == 0) return
        val next = ((_ui.value.selectedTrip ?: -1) + delta).coerceIn(0, count - 1)
        selectTrip(next)
    }

    fun load() {
        job?.cancel()
        if (serial.isBlank()) {
            _ui.update { it.copy(loading = false, error = "Brak numeru urządzenia GPS dla tego pojazdu.") }
            return
        }
        val s = _ui.value
        val zone = Fmt.zone
        val start = s.from.atStartOfDayIn(zone)
        val end = minOf(s.to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.seconds, now())
        _ui.update { it.copy(loading = true, error = null, selectedTrip = null, selectedStop = null) }
        job = viewModelScope.launch {
            try {
                val points = repo.routes(serial, start, end)
                val analysis = withContext(Dispatchers.Default) { analyzeRoute(points) }
                _ui.update { it.copy(loading = false, analysis = analysis) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Nie udało się pobrać tras") }
            }
        }
    }
}
