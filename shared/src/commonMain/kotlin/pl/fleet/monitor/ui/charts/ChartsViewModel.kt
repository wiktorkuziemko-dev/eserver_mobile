package pl.fleet.monitor.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.RoutePoint
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Jedna seria wykresu: punkty (czas, wartość) – null to brak odczytu. */
data class Series(val points: List<Pair<Instant, Double?>>) {
    val hasData: Boolean get() = points.any { it.second != null }
}

data class ChartsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val speed: Series = Series(emptyList()),
    val rpm: Series = Series(emptyList()),
    val voltage: Series = Series(emptyList()),
    val fuel: Series = Series(emptyList()),
    /** Wybrana chwila (dotknięcie wykresu) – wspólna dla wszystkich wykresów. */
    val cursor: Instant? = null,
    /** Oś czasu złożona z samych okresów jazdy (postoje pominięte). */
    val axis: TimeAxis = TimeAxis(emptyList()),
)

/**
 * Oś czasu z samych okresów jazdy: kolejne trasy stoją obok siebie, postoje są pominięte,
 * więc 30 minut jazdy w ciągu doby zajmuje całą szerokość wykresu.
 */
class TimeAxis(val segments: List<Pair<Instant, Instant>>) {
    private val durations = segments.map { (it.second - it.first).inWholeMilliseconds.coerceAtLeast(1) }
    private val offsets = durations.runningFold(0L) { a, d -> a + d }
    val totalMs: Long = offsets.last().coerceAtLeast(1)
    val drivingSeconds: Long get() = durations.sum() / 1000

    /** Odcinek, do którego należy chwila [t] (null – postój, poza osią). */
    fun segmentOf(t: Instant): Int? = segments.indexOfFirst { t >= it.first && t <= it.second }.takeIf { it >= 0 }

    /** Położenie chwili [t] na osi (0–1); null dla chwil poza okresami jazdy. */
    fun fraction(t: Instant): Float? {
        val i = segmentOf(t) ?: return null
        return (offsets[i] + (t - segments[i].first).inWholeMilliseconds).toFloat() / totalMs
    }

    /** Chwila odpowiadająca położeniu [f] (0–1) na osi. */
    fun timeAt(f: Float): Instant? {
        if (segments.isEmpty()) return null
        val ms = (f.coerceIn(0f, 1f) * totalMs).toLong()
        val i = (offsets.indexOfLast { it <= ms }).coerceIn(0, segments.lastIndex)
        return segments[i].first + (ms - offsets[i]).coerceAtMost(durations[i]).milliseconds
    }

    /** Początki odcinków: położenie na osi i godzina – do podpisów i linii podziału. */
    val starts: List<Pair<Float, Instant>> get() = segments.mapIndexed { i, s -> offsets[i].toFloat() / totalMs to s.first }
}

/** Wykresy prędkości, obrotów, napięcia i paliwa z raportów nadajnika (funkcja routes) dla trasy lub dnia. */
class ChartsViewModel(private val serial: String, val from: Instant, val to: Instant) : ViewModel() {
    private val repo = AppGraph.container.repository
    private val _ui = MutableStateFlow(ChartsUiState())
    val ui: StateFlow<ChartsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // Okresy jazdy z historii (jak w Statystyce); bez historii – z analizy samych punktów.
                val history = try {
                    repo.history(serial, from, to, details = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                // Mały zapas zakresu – punkty i tak przycinamy do okresów jazdy.
                val all = repo.routes(serial, from - 5.minutes, to + 5.minutes).filter { it.time in from..to }
                val trips = history?.entries.orEmpty().filter { it.motion && it.begin != null && it.end != null }
                    .map { maxOf(it.begin!!, from) to minOf(it.end!!, to) }
                    .ifEmpty { pl.fleet.monitor.data.analyzeRoute(all).trips.map { it.start to it.end } }
                    .filter { it.second > it.first }
                    .sortedBy { it.first }
                val axis = TimeAxis(trips)
                val points = all.filter { axis.segmentOf(it.time) != null }
                fun series(f: (RoutePoint) -> Double?) = Series(points.map { it.time to f(it) })
                _ui.update {
                    it.copy(
                        loading = false,
                        axis = axis,
                        speed = series { p -> p.speed.takeIf { v -> !v.isNaN() } },
                        rpm = series { p -> p.rpm },
                        voltage = series { p -> p.voltage?.takeIf { v -> v > 0 } },
                        fuel = series { p -> p.fuel?.takeIf { v -> v > 0 } },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Nie udało się pobrać danych") }
            }
        }
    }

    fun setCursor(t: Instant?) = _ui.update { it.copy(cursor = t) }
}
