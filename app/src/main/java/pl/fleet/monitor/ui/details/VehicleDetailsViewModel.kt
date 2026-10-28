package pl.fleet.monitor.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.FleetApp
import pl.fleet.monitor.data.HistorySummary
import pl.fleet.monitor.data.MotionStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Wynik pojedynczego zapytania – każde z trzech ładujemy niezależnie, aby błąd jednego nie blokował reszty. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

data class DetailsUiState(
    val status: Loadable<MotionStatus> = Loadable.Loading,
    val lastReport: Loadable<ZonedDateTime?> = Loadable.Loading,
    val today: Loadable<HistorySummary> = Loadable.Loading,
    val diagnostics: Loadable<String>? = null,
)

class VehicleDetailsViewModel(app: Application, private val serial: String) : AndroidViewModel(app) {
    private val repo = (app as FleetApp).repository

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
                val zone = ZoneId.systemDefault()
                result { repo.history(serial, LocalDate.now().atStartOfDay(zone), ZonedDateTime.now(zone), details = false) }
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
