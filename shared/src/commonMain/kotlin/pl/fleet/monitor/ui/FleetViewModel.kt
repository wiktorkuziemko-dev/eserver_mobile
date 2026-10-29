package pl.fleet.monitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.now
import pl.fleet.monitor.data.ApiException
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.map.FleetMapState
import kotlin.time.Instant

enum class MainTab { MAP, LIST, SETTINGS }

data class FleetUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastRefresh: Instant? = null,
    val query: String = "",
    val stateFilter: VehicleState? = null,
    val selectedId: String? = null,
    val tab: MainTab = MainTab.MAP,
    val showDriving: Boolean = true,
    val showParked: Boolean = true,
    val hidden: Set<String> = emptySet(),
    val icons: Map<String, VehicleIcon> = emptyMap(),
    val customLabels: Map<String, String> = emptyMap(),
) {
    /** Opis pojazdu na mapie: własny z ustawień albo nazwa pojazdu. */
    fun mapLabel(v: Vehicle): String = customLabels[v.deviceId] ?: v.displayName.ifBlank { v.label }

    val selected: Vehicle? get() = vehicles.firstOrNull { it.deviceId == selectedId }

    val filtered: List<Vehicle>
        get() = vehicles.filter { v ->
            v.deviceId !in hidden && (stateFilter == null || v.state == stateFilter) &&
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
/** Pozycje pojazdów odświeżane są zawsze co 30 sekund. */
private const val REFRESH_INTERVAL_MS = 30_000L

class FleetViewModel : ViewModel() {
    private val container = AppGraph.container
    private val repo = container.repository
    val session = container.session
    val appVersion: String get() = container.appVersion

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
            if (last == null || (now() - last).inWholeSeconds >= 5) load()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private suspend fun load() {
        if (session.credentials.value == null) return
        _ui.update { it.copy(loading = true) }
        try {
            val list = repo.vehicles()
            _ui.update { it.copy(vehicles = list, loading = false, error = null, lastRefresh = now()) }
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
    fun showAllTypes() = _ui.update {
        session.showDriving = true; session.showParked = true
        it.copy(showDriving = true, showParked = true)
    }
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
        icons = session.vehicleIcons.mapValues { VehicleIcon.fromKey(it.value) },
        customLabels = session.vehicleLabels,
    )

    /** Ikona i własny opis pojazdu (pusty opis = nazwa pojazdu). */
    fun setAppearance(deviceId: String, icon: VehicleIcon, label: String) {
        setIcon(deviceId, icon)
        _ui.update {
            val clean = label.replace('\n', ' ').trim()
            val labels = if (clean.isEmpty()) it.customLabels - deviceId else it.customLabels + (deviceId to clean)
            session.vehicleLabels = labels
            it.copy(customLabels = labels)
        }
    }

    fun setIcon(deviceId: String, icon: VehicleIcon) = _ui.update {
        val icons = if (icon == VehicleIcon.DEFAULT) it.icons - deviceId else it.icons + (deviceId to icon)
        session.vehicleIcons = icons.mapValues { e -> e.value.name }
        it.copy(icons = icons)
    }

    fun logout() {
        session.logout()
        mapState.hasFitted = false
        _ui.value = initialState()
    }
}
