package pl.fleet.monitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.FleetApp
import pl.fleet.monitor.data.ApiException
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.map.FleetMapState
import java.time.ZonedDateTime

enum class MainTab { MAP, LIST, SETTINGS }

data class FleetUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastRefresh: ZonedDateTime? = null,
    val query: String = "",
    val stateFilter: VehicleState? = null,
    val selectedId: String? = null,
    val tab: MainTab = MainTab.MAP,
    val showDriving: Boolean = true,
    val showParked: Boolean = true,
    val hidden: Set<String> = emptySet(),
) {
    val selected: Vehicle? get() = vehicles.firstOrNull { it.deviceId == selectedId }

    val filtered: List<Vehicle>
        get() = vehicles.filter { v ->
            (stateFilter == null || v.state == stateFilter) &&
                (query.isBlank() || listOf(v.plateNo, v.name, v.serial).any { it.contains(query.trim(), ignoreCase = true) })
        }

    fun count(state: VehicleState) = vehicles.count { it.state == state }

    /** Pojazdy włączone przełącznikiem „Pokazuj na mapie”. */
    private val onMap: List<Vehicle> get() = vehicles.filter { it.deviceId !in hidden }

    val drivingOnMap: Int get() = onMap.count { it.state == VehicleState.DRIVING }
    val parkedOnMap: Int get() = onMap.count { it.state != VehicleState.DRIVING }

    /** Pojazdy widoczne na mapie: przełącznik na liście + filtr jadące / zaparkowane. */
    val mapVehicles: List<Vehicle>
        get() = onMap.filter { if (it.state == VehicleState.DRIVING) showDriving else showParked }
}

/** Współdzielony stan floty – wykorzystywany przez mapę, listę i szczegóły pojazdu. */
class FleetViewModel(app: Application) : AndroidViewModel(app) {
    private val fleetApp = app as FleetApp
    private val repo = fleetApp.repository
    val session = fleetApp.session

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<FleetUiState> = _ui.asStateFlow()

    val mapState = FleetMapState()

    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { load() }
    }

    /** Pętla automatycznego odświeżania – uruchamiana tylko, gdy aplikacja jest na pierwszym planie. */
    suspend fun autoRefreshLoop() {
        while (true) {
            refreshJob?.join()
            val last = _ui.value.lastRefresh
            if (last == null || java.time.Duration.between(last, ZonedDateTime.now()).seconds >= 5) load()
            delay(session.refreshIntervalSec * 1000L)
        }
    }

    private suspend fun load() {
        if (session.credentials.value == null) return
        _ui.update { it.copy(loading = true) }
        try {
            val list = repo.vehicles()
            _ui.update { it.copy(vehicles = list, loading = false, error = null, lastRefresh = ZonedDateTime.now()) }
        } catch (e: CancellationException) {
            _ui.update { it.copy(loading = false) }
            throw e
        } catch (e: ApiException.Unauthorized) {
            _ui.update { it.copy(loading = false, error = e.message) }
            logout()
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.message ?: "Nieznany błąd") }
        }
    }

    fun select(v: Vehicle?) = _ui.update { it.copy(selectedId = v?.deviceId) }
    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setFilter(s: VehicleState?) = _ui.update { it.copy(stateFilter = if (it.stateFilter == s) null else s) }
    fun setTab(t: MainTab) = _ui.update { it.copy(tab = t) }

    fun showOnMap(deviceId: String) {
        // Pojazd musi być widoczny – włączamy go, jeśli był ukryty lub odfiltrowany.
        if (deviceId in _ui.value.hidden) setOnMap(deviceId, true)
        _ui.value.vehicles.firstOrNull { it.deviceId == deviceId }?.let { v ->
            if (v.state == VehicleState.DRIVING && !_ui.value.showDriving) toggleDriving()
            if (v.state != VehicleState.DRIVING && !_ui.value.showParked) toggleParked()
        }
        _ui.update { it.copy(tab = MainTab.MAP, selectedId = deviceId) }
        _ui.value.selected?.let { mapState.focusOn(it, 16.0) }
    }

    fun toggleDriving() = _ui.update { it.copy(showDriving = !it.showDriving).also { s -> session.showDriving = s.showDriving } }
    fun toggleParked() = _ui.update { it.copy(showParked = !it.showParked).also { s -> session.showParked = s.showParked } }

    fun setOnMap(deviceId: String, visible: Boolean) = _ui.update {
        val hidden = if (visible) it.hidden - deviceId else it.hidden + deviceId
        session.hiddenVehicles = hidden
        it.copy(hidden = hidden, selectedId = if (!visible && it.selectedId == deviceId) null else it.selectedId)
    }

    fun setAllOnMap(visible: Boolean) = _ui.update {
        val hidden = if (visible) emptySet() else it.vehicles.map { v -> v.deviceId }.toSet()
        session.hiddenVehicles = hidden
        it.copy(hidden = hidden)
    }

    private fun initialState() = FleetUiState(
        showDriving = session.showDriving,
        showParked = session.showParked,
        hidden = session.hiddenVehicles,
    )

    fun logout() {
        session.logout()
        mapState.hasFitted = false
        _ui.value = initialState()
    }
}
