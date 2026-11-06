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
import pl.fleet.monitor.data.RoutePlayback
import pl.fleet.monitor.data.RouteSample
import kotlinx.coroutines.delay
import kotlin.time.Instant
import kotlin.time.TimeSource
import pl.fleet.monitor.data.analyzeRoute
import pl.fleet.monitor.data.now
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.history.RangePreset
import kotlin.time.Duration.Companion.seconds

/** Maksymalna długość okresu tras na mapie (w dniach). */
const val MAX_ROUTE_DAYS = 7

/** Szybkie zakresy na mapie tras (dłuższe okresy – przez „Zakres…”, maks. 7 dni). */
val ROUTE_PRESETS = listOf(RangePreset.TODAY, RangePreset.YESTERDAY, RangePreset.DAY_BEFORE)

data class RouteMapUiState(
    val preset: RangePreset = RangePreset.TODAY,
    val from: LocalDate = Fmt.today(),
    val to: LocalDate = Fmt.today(),
    val loading: Boolean = false,
    val error: String? = null,
    val analysis: RouteAnalysis? = null,
    val selectedTrip: Int? = null,
    val selectedStop: Int? = null,
    /** Czy po wyborze trasy dopasować do niej widok (podczas odtwarzania – nie). */
    val fitOnSelect: Boolean = true,
    val player: PlayerState = PlayerState(),
    val sample: RouteSample? = null,
)

/** Odtwarzanie przejazdu: [factor] – ile sekund trasy na sekundę odtwarzania. */
data class PlayerState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val time: Instant? = null,
    val factor: Int = 60,
    val skipStops: Boolean = true,
    val follow: Boolean = true,
)

val PLAYER_FACTORS = listOf(10, 60, 300)

class RouteMapViewModel(private val serial: String) : ViewModel() {
    private val repo = AppGraph.container.repository

    private val _ui = MutableStateFlow(RouteMapUiState())
    val ui: StateFlow<RouteMapUiState> = _ui.asStateFlow()

    val mapState = RouteMapState()
    private var job: Job? = null
    private var playJob: Job? = null
    private var playback: RoutePlayback? = null

    init { load() }

    fun selectPreset(p: RangePreset) {
        val today = Fmt.today()
        val (from, to) = when (p) {
            RangePreset.TODAY -> today to today
            RangePreset.YESTERDAY -> today.minus(1, DateTimeUnit.DAY) to today.minus(1, DateTimeUnit.DAY)
            RangePreset.DAY_BEFORE -> today.minus(2, DateTimeUnit.DAY) to today.minus(2, DateTimeUnit.DAY)
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

    fun selectTrip(i: Int?) = _ui.update { it.copy(selectedTrip = i, selectedStop = null, fitOnSelect = true) }

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
        closePlayer()
        _ui.update { it.copy(loading = true, error = null, selectedTrip = null, selectedStop = null) }
        job = viewModelScope.launch {
            try {
                val points = repo.routes(serial, start, end)
                val analysis = withContext(Dispatchers.Default) { analyzeRoute(points) }
                playback = RoutePlayback(analysis)
                _ui.update { it.copy(loading = false, analysis = analysis) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Nie udało się pobrać tras") }
            }
        }
    }

    // ---- Odtwarzanie przejazdu ----

    fun togglePlay() {
        val pb = playback ?: return
        val start = pb.start ?: return
        val end = pb.end ?: return
        val p = _ui.value.player
        if (p.playing) {
            playJob?.cancel()
            _ui.update { it.copy(player = it.player.copy(playing = false)) }
            return
        }
        // Start: od wybranej trasy, od miejsca zatrzymania albo od początku okresu (po końcu – od nowa).
        val from = when {
            !p.active -> _ui.value.selectedTrip?.let { _ui.value.analysis?.trips?.getOrNull(it)?.start } ?: start
            p.time == null || p.time >= end -> start
            else -> p.time
        }
        _ui.update { it.copy(player = it.player.copy(active = true, playing = true), selectedStop = null) }
        seek(from)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            var mark = TimeSource.Monotonic.markNow()
            while (true) {
                delay(FRAME_MS)
                val elapsed = mark.elapsedNow()
                mark = TimeSource.Monotonic.markNow()
                val cur = _ui.value.player
                var t = (cur.time ?: start) + elapsed * cur.factor
                if (cur.skipStops) pb.stopAt(t)?.let { st -> if (st.end > t) t = st.end }
                if (t >= end) {
                    seek(end)
                    _ui.update { it.copy(player = it.player.copy(playing = false)) }
                    break
                }
                seek(t)
            }
        }
    }

    /** Przewinięcie suwakiem: [fraction] 0–1 okresu. */
    fun seekFraction(fraction: Float) {
        val pb = playback ?: return
        val start = pb.start ?: return
        val end = pb.end ?: return
        _ui.update { it.copy(player = it.player.copy(active = true)) }
        seek(start + (end - start) * fraction.toDouble().coerceIn(0.0, 1.0))
    }

    fun setFactor(f: Int) = _ui.update { it.copy(player = it.player.copy(factor = f)) }
    fun toggleSkipStops() = _ui.update { it.copy(player = it.player.copy(skipStops = !it.player.skipStops)) }
    fun toggleFollow() = _ui.update { it.copy(player = it.player.copy(follow = !it.player.follow)) }

    fun closePlayer() {
        playJob?.cancel()
        mapState.hideVehicle()
        _ui.update { it.copy(player = PlayerState(factor = it.player.factor, skipStops = it.player.skipStops, follow = it.player.follow), sample = null) }
    }

    private fun seek(t: Instant) {
        val s = playback?.sampleAt(t) ?: return
        _ui.update {
            // Bieżąca trasa jest wyróżniana na mapie (bez zmiany widoku – kamera podąża za pojazdem).
            val trip = s.trip ?: it.selectedTrip
            it.copy(player = it.player.copy(time = s.time), sample = s, selectedTrip = trip, fitOnSelect = false)
        }
        mapState.showVehicle(s, follow = _ui.value.player.follow)
    }

    override fun onCleared() {
        playJob?.cancel()
    }
}

private const val FRAME_MS = 40L
