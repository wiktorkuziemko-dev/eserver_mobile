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
)

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
                // Mały zapas zakresu – punkty i tak przycinamy do [from, to].
                val points = repo.routes(serial, from - 5.minutes, to + 5.minutes).filter { it.time in from..to }
                fun series(f: (RoutePoint) -> Double?) = Series(points.map { it.time to f(it) })
                _ui.update {
                    it.copy(
                        loading = false,
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
