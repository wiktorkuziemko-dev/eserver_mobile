package pl.fleet.monitor.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.atStartOfDayIn
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.now
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.data.HistorySummary
import pl.fleet.monitor.data.MotionStatus
import kotlin.time.Instant

/** Wynik pojedynczego zapytania – każde z trzech ładujemy niezależnie, aby błąd jednego nie blokował reszty. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

data class DetailsUiState(
    val status: Loadable<MotionStatus> = Loadable.Loading,
    val lastReport: Loadable<Instant?> = Loadable.Loading,
    val today: Loadable<HistorySummary> = Loadable.Loading,
    val diagnostics: Loadable<String>? = null,
)

class VehicleDetailsViewModel(private val serial: String) : ViewModel() {
    private val repo = AppGraph.container.repository

    private val _ui = MutableStateFlow(DetailsUiState())
    val ui: StateFlow<DetailsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        if (serial.isBlank()) {
            val err = Loadable.Failed("Brak numeru urządzenia GPS")
            _ui.value = DetailsUiState(err, err, err)
            return
        }
        _ui.update { DetailsUiState(diagnostics = it.diagnostics) }
        viewModelScope.launch {
            val status = async { result { repo.status(serial) } }
            val last = async { result { repo.lastReport(serial) } }
            val today = async {
                result { repo.history(serial, Fmt.today().atStartOfDayIn(Fmt.zone), now(), details = false) }
            }
            _ui.update { it.copy(status = status.await()) }
            _ui.update { it.copy(lastReport = last.await()) }
            _ui.update { it.copy(today = today.await()) }
        }
    }

    fun runDiagnostics() {
        _ui.update { it.copy(diagnostics = Loadable.Loading) }
        viewModelScope.launch {
            val r = result { repo.diagnostics(serial) }
            _ui.update { it.copy(diagnostics = r) }
        }
    }

    fun closeDiagnostics() = _ui.update { it.copy(diagnostics = null) }

    private suspend fun <T> result(block: suspend () -> T): Loadable<T> = try {
        Loadable.Ready(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Loadable.Failed(e.message ?: "Błąd")
    }
}
