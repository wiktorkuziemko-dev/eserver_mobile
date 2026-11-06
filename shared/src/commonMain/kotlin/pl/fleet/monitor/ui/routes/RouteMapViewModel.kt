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
    /** Zdarzenie (postój, start, koniec, postój na trasie), którego szczegóły pokazuje karta. */
    val selectedEvent: MapEvent? = null,
    /** Czy po wyborze trasy dopasować do niej widok (podczas odtwarzania – nie). */
    val fitOnSelect: Boolean = true,
    val player: PlayerState = PlayerState(),
    val sample: RouteSample? = null,
)

/** Tempo odtwarzania: [factor] – ile sekund trasy na sekundę animacji. */
enum class PlaybackSpeed(val label: String, val factor: Int) { SLOW("Wolno", 20), FAST("Szybko", 60), ULTRA("Ultra", 240) }

/** Odtwarzanie wybranej trasy (całego zakresu nie animujemy). */
data class PlayerState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val time: Instant? = null,
    val speed: PlaybackSpeed = PlaybackSpeed.FAST,
    /** Mapa podąża za pojazdem (przy przybliżeniu ustawionym przez użytkownika). */
    val follow: Boolean = false,
    /** Animacja całego zakresu (gdy nie wybrano trasy) – postoje są wtedy przeskakiwane. */
    val wholeRange: Boolean = false,
    /** Dystans przejechany od początku wybranej trasy [km]. */
    val km: Double = 0.0,
)

class RouteMapViewModel(private val serial: String) : ViewModel() {
    private val repo = AppGraph.container.repository

    private val _ui = MutableStateFlow(RouteMapUiState())
    val ui: StateFlow<RouteMapUiState> = _ui.asStateFlow()

    val mapState = RouteMapState()
    private val session = AppGraph.container.session

    /** Ustawienie „Pokazuj legendę w trasach na mapie”. */
    val showLegend: Boolean get() = session.showRouteLegend
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

    /** Wybór trasy pokazuje na mapie tylko ją; null = wszystkie trasy. Zmiana trasy kończy odtwarzanie. */
    fun selectTrip(i: Int?) {
        if (i != _ui.value.selectedTrip) closePlayer()
        _ui.update { it.copy(selectedTrip = i, selectedEvent = null, fitOnSelect = true) }
    }

    /** Postój wybrany z listy: widok wszystkich tras, karta postoju i przybliżenie do niego. */
    fun showStop(i: Int) {
        selectTrip(null)
        selectEvent(MapEvent.Stop(i))
    }

    fun selectEvent(e: MapEvent?) {
        if (_ui.value.player.active && e != null) return
        _ui.update { it.copy(selectedEvent = e) }
        if (e is MapEvent.Stop) _ui.value.analysis?.stops?.getOrNull(e.index)?.let(mapState::focusStop)
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
        _ui.update { it.copy(loading = true, error = null, selectedTrip = null, selectedEvent = null) }
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

    // ---- Odtwarzanie wybranej trasy ----

    /** Zakres animacji: wybrana trasa albo – gdy żadnej nie wybrano – cały widoczny okres. */
    private fun selectedRange(): Pair<Instant, Instant>? {
        val sel = _ui.value.selectedTrip
        if (sel != null) {
            val tr = _ui.value.analysis?.trips?.getOrNull(sel) ?: return null
            return tr.start to tr.end
        }
        val pb = playback ?: return null
        return (pb.start ?: return null) to (pb.end ?: return null)
    }

    fun togglePlay() {
        val (start, end) = selectedRange() ?: return
        val p = _ui.value.player
        if (p.playing) {
            playJob?.cancel()
            _ui.update { it.copy(player = it.player.copy(playing = false)) }
            return
        }
        // Od miejsca zatrzymania; po dojechaniu do końca – od początku trasy.
        val from = p.time?.takeIf { p.active && it >= start && it < end } ?: start
        val whole = _ui.value.selectedTrip == null
        _ui.update { it.copy(player = it.player.copy(active = true, playing = true, wholeRange = whole), selectedEvent = null) }
        seek(from)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            var mark = TimeSource.Monotonic.markNow()
            while (true) {
                delay(FRAME_MS)
                val elapsed = mark.elapsedNow()
                mark = TimeSource.Monotonic.markNow()
                val cur = _ui.value.player
                var t = (cur.time ?: start) + elapsed * cur.speed.factor
                // Cały zakres: postoje przeskakujemy (inaczej noc trwałaby kilka minut animacji).
                if (cur.wholeRange) playback?.stopAt(t)?.let { st -> if (st.end > t) t = st.end }
                if (t >= end) {
                    seek(end)
                    _ui.update { it.copy(player = it.player.copy(playing = false)) }
                    break
                }
                seek(t)
            }
        }
    }

    /** Przewinięcie suwakiem: [fraction] 0–1 czasu wybranej trasy. */
    fun seekFraction(fraction: Float) {
        val (start, end) = selectedRange() ?: return
        _ui.update { it.copy(player = it.player.copy(active = true, wholeRange = it.selectedTrip == null)) }
        seek(start + (end - start) * fraction.toDouble().coerceIn(0.0, 1.0))
    }

    fun setSpeed(speed: PlaybackSpeed) = _ui.update { it.copy(player = it.player.copy(speed = speed)) }

    fun toggleFollow() {
        _ui.update { it.copy(player = it.player.copy(follow = !it.player.follow)) }
        _ui.value.sample?.let { mapState.showVehicle(it, follow = _ui.value.player.follow) }
    }

    fun closePlayer() {
        playJob?.cancel()
        mapState.hideVehicle()
        _ui.update { it.copy(player = PlayerState(speed = it.player.speed, follow = it.player.follow), sample = null) }
    }

    private fun seek(t: Instant) {
        val s = playback?.sampleAt(t) ?: return
        val startKm = selectedRange()?.let { playback?.sampleAt(it.first)?.km } ?: s.km
        _ui.update { it.copy(player = it.player.copy(time = s.time, km = (s.km - startKm).coerceAtLeast(0.0)), sample = s) }
        // Ze śledzeniem mapa przesuwa się za pojazdem, nie zmieniając przybliżenia ustawionego przez użytkownika.
        mapState.showVehicle(s, follow = _ui.value.player.follow)
    }

    override fun onCleared() {
        playJob?.cancel()
    }
}

private const val FRAME_MS = 40L
