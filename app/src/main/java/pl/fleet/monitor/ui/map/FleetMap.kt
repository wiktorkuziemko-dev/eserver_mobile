package pl.fleet.monitor.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.graphics.toArgb
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.color

/** Uchwyt pozwalający sterować mapą spoza kompozycji (dopasowanie widoku, wycentrowanie na pojeździe). */
class FleetMapState {
    internal var mapView: MapView? = null
    internal var pendingFit: List<Vehicle>? = null
    internal var pendingFocus: Pair<Vehicle, Double>? = null
    internal var laidOut = false
    internal var savedCenter: GeoPoint? = null
    internal var savedZoom: Double? = null

    /** Czy widok był już dopasowany do pojazdów (żeby nie nadpisywać przybliżenia ustawionego przez użytkownika). */
    var hasFitted = false

    fun fitAll(vehicles: List<Vehicle>) {
        val map = mapView
        if (map == null || !laidOut) { pendingFit = vehicles; return }
        hasFitted = true
        val points = vehicles.filter { it.hasPosition }.map { GeoPoint(it.latitude!!, it.longitude!!) }
        when {
            points.isEmpty() -> return
            points.size == 1 -> {
                map.controller.setZoom(15.0)
                map.controller.setCenter(points.first())
            }
            else -> {
                val box = BoundingBox.fromGeoPointsSafe(points)
                val pad = (64 * map.context.resources.displayMetrics.density).toInt()
                map.zoomToBoundingBox(box.increaseByScale(1.15f), false, pad)
                if (map.zoomLevelDouble > 16.0) map.controller.setZoom(16.0)
            }
        }
    }

    fun focusOn(v: Vehicle, zoom: Double = 15.0) {
        if (!v.hasPosition) return
        hasFitted = true
        val map = mapView
        if (map == null || !laidOut) { pendingFocus = v to zoom; pendingFit = null; return }
        map.controller.animateTo(GeoPoint(v.latitude!!, v.longitude!!), maxOf(zoom, map.zoomLevelDouble), 600L)
    }
}

@Composable
fun FleetMap(
    vehicles: List<Vehicle>,
    modifier: Modifier = Modifier,
    state: FleetMapState = remember { FleetMapState() },
    selectedId: String? = null,
    onSelect: (Vehicle?) -> Unit = {},
) {
    val context = LocalContext.current
    val icons = remember { MarkerIcons(context) }
    val markers = remember { HashMap<String, Marker>() }
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentVehicles = rememberUpdatedState(vehicles)

    val pulse = remember { PulseOverlay(context.resources.displayMetrics.density, VehicleState.DRIVING.color.toArgb()) }
    val mapView = remember { createMapView(context, state, pulse) { currentOnSelect.value(null) } }
    state.mapView = mapView

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.savedCenter = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
            state.savedZoom = mapView.zoomLevelDouble
            mapView.onPause()
            mapView.onDetach()
            if (state.mapView === mapView) {
                state.mapView = null
                state.laidOut = false
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            pulse.points = vehicles.filter { it.hasPosition && it.state == VehicleState.DRIVING }
                .map { GeoPoint(it.latitude!!, it.longitude!!) }

            val ids = vehicles.filter { it.hasPosition }.map { it.deviceId }.toSet()
            markers.keys.filter { it !in ids }.forEach { id -> map.overlays.remove(markers.remove(id)) }

            vehicles.filter { it.hasPosition }.forEach { v ->
                val marker = markers.getOrPut(v.deviceId) {
                    Marker(map).also { m ->
                        m.setOnMarkerClickListener { mk, _ ->
                            currentVehicles.value.firstOrNull { it.deviceId == mk.id }?.let { currentOnSelect.value(it) }
                            true
                        }
                        map.overlays.add(m)
                    }
                }
                val icon = icons.get(v, v.deviceId == selectedId)
                marker.id = v.deviceId
                marker.title = v.label
                marker.position = GeoPoint(v.latitude!!, v.longitude!!)
                marker.icon = icon.drawable
                marker.setAnchor(Marker.ANCHOR_CENTER, icon.anchorY)
            }
            // Zaznaczony pojazd zawsze na wierzchu.
            selectedId?.let { id -> markers[id]?.let { m -> map.overlays.remove(m); map.overlays.add(m) } }
            map.invalidate()
        },
    )
}

private fun createMapView(context: Context, state: FleetMapState, pulse: PulseOverlay, onMapTap: () -> Unit): MapView =
    MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        setMinZoomLevel(3.0)
        setMaxZoomLevel(19.0)
        controller.setZoom(state.savedZoom ?: 6.0)
        controller.setCenter(state.savedCenter ?: GeoPoint(52.07, 19.48)) // domyślnie środek Polski
        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { onMapTap(); return false }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        overlays.add(pulse) // pod znacznikami – te są dodawane później
        addOnFirstLayoutListener { _, _, _, _, _ ->
            state.laidOut = true
            state.pendingFit?.let { state.pendingFit = null; state.fitAll(it) }
            state.pendingFocus?.let { (v, z) ->
                state.pendingFocus = null
                controller.setZoom(z)
                controller.setCenter(GeoPoint(v.latitude!!, v.longitude!!))
            }
        }
    }
