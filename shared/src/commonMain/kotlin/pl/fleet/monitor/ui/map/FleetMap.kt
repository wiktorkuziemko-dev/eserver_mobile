package pl.fleet.monitor.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.onTap
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.setMapBackground
import ovh.plrapps.mapcompose.api.updateMarkerZ
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.data.platformHttpClient
import pl.fleet.monitor.ui.VehicleIcon
import pl.fleet.monitor.ui.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

// ---- Siatka kafelków OpenStreetMap (Web Mercator, poziomy 0–18) ----

private const val LEVELS = 19
private const val TILE_SIZE = 256
private const val FULL_SIZE = TILE_SIZE shl (LEVELS - 1)

private fun lonToX(lon: Double) = ((lon + 180.0) / 360.0).coerceIn(0.0, 1.0)

private fun latToY(lat: Double): Double {
    val r = lat.coerceIn(-85.0, 85.0) * PI / 180.0
    return ((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0).coerceIn(0.0, 1.0)
}

/** Skala MapCompose odpowiadająca poziomowi przybliżenia OSM (18 = skala 1). */
private fun zoomToScale(zoom: Double) = 2.0.pow(zoom - (LEVELS - 1))

private val tileClient by lazy { platformHttpClient() }

/** Kafelki pobierane z serwera OSM (wymagany identyfikujący User-Agent). */
private val osmTiles = TileStreamProvider { row, col, zoom ->
    try {
        val r = tileClient.get("https://tile.openstreetmap.org/$zoom/$col/$row.png") {
            header(HttpHeaders.UserAgent, "E-SERVER-mini (pl.fleet.monitor)")
        }
        if (r.status.isSuccess()) Buffer().apply { write(r.readRawBytes()) } else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** Dane znacznika – zmiana wartości odświeża tylko ten znacznik. */
internal data class MarkerUi(val label: String, val state: VehicleState, val icon: VehicleIcon, val selected: Boolean)

/** Stan mapy (pozycja i przybliżenie) – przeżywa przełączanie zakładek, bo trzyma go ViewModel. */
class FleetMapState {
    internal val map = MapState(LEVELS, FULL_SIZE, FULL_SIZE, TILE_SIZE) {
        maxScale(2.0)
        scale(zoomToScale(6.0))
        scroll(lonToX(19.48), latToY(52.07)) // domyślnie środek Polski
    }.apply {
        addLayer(osmTiles)
        setMapBackground(Color(0xFFEFEFEA))
    }

    internal val markerData = mutableStateMapOf<String, MarkerUi>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Czy widok był już dopasowany do pojazdów (żeby nie nadpisywać przybliżenia ustawionego przez użytkownika). */
    var hasFitted = false

    fun fitAll(vehicles: List<Vehicle>) {
        val points = vehicles.filter { it.hasPosition }.map { lonToX(it.longitude!!) to latToY(it.latitude!!) }
        if (points.isEmpty()) return
        hasFitted = true
        scope.launch {
            if (points.size == 1) {
                map.scrollTo(points[0].first, points[0].second, zoomToScale(15.0))
            } else {
                val box = BoundingBox(
                    xLeft = points.minOf { it.first }, yTop = points.minOf { it.second },
                    xRight = points.maxOf { it.first }, yBottom = points.maxOf { it.second },
                )
                map.scrollTo(box, padding = Offset(0.3f, 0.45f))
                if (map.scale > zoomToScale(16.0)) map.scale = zoomToScale(16.0)
            }
        }
    }

    fun focusOn(v: Vehicle, zoom: Double = 15.0) {
        if (!v.hasPosition) return
        hasFitted = true
        scope.launch { map.scrollTo(lonToX(v.longitude!!), latToY(v.latitude!!), max(zoomToScale(zoom), map.scale)) }
    }
}

@Composable
fun FleetMap(
    vehicles: List<Vehicle>,
    modifier: Modifier = Modifier,
    state: FleetMapState = remember { FleetMapState() },
    selectedId: String? = null,
    icons: Map<String, VehicleIcon> = emptyMap(),
    labels: Map<String, String> = emptyMap(),
    onSelect: (Vehicle?) -> Unit = {},
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentVehicles by rememberUpdatedState(vehicles)

    LaunchedEffect(state) {
        state.map.onMarkerClick { id, _, _ -> currentVehicles.firstOrNull { it.deviceId == id }?.let { currentOnSelect(it) } }
        state.map.onTap { _, _ -> currentOnSelect(null) }
    }

    // Synchronizacja znaczników z listą pojazdów
    LaunchedEffect(vehicles, selectedId, icons, labels) {
        val wanted = vehicles.filter { it.hasPosition }
        val ids = wanted.map { it.deviceId }.toSet()
        (state.markerData.keys - ids).forEach { id ->
            state.map.removeMarker(id)
            state.markerData.remove(id)
        }
        wanted.forEach { v ->
            val x = lonToX(v.longitude!!)
            val y = latToY(v.latitude!!)
            val selected = v.deviceId == selectedId
            val ui = MarkerUi(
                label = labels[v.deviceId] ?: v.displayName.ifBlank { v.label },
                state = v.state,
                icon = icons[v.deviceId] ?: VehicleIcon.DEFAULT,
                selected = selected,
            )
            if (v.deviceId in state.markerData) {
                state.map.moveMarker(v.deviceId, x, y)
            } else {
                val id = v.deviceId
                state.map.addMarker(id, x, y, relativeOffset = Offset(-0.5f, -0.5f)) {
                    state.markerData[id]?.let { VehicleMarker(it) }
                }
            }
            state.markerData[v.deviceId] = ui
            state.map.updateMarkerZ(v.deviceId, if (selected) 2f else if (v.state == VehicleState.DRIVING) 1f else 0f)
        }
    }

    Box(modifier) {
        MapUI(Modifier.matchParentSize(), state = state.map)
        // Wymagane oznaczenie autorstwa danych mapy
        Text(
            "© OpenStreetMap",
            Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 9.sp,
            color = Color(0xFF3F4045),
        )
    }
}

// ---- Znacznik pojazdu ----

private val SELECTED = Color(0xFF1E3A8A)
private val LABEL_HEIGHT = 20.dp
private val GLYPH = 34.dp

/**
 * Ikona pojazdu w kolorze stanu (zielony w ruchu, pomarańczowy na postoju) z białym obrysem,
 * pod nią etykieta z nazwą. Środek ikony to punkt zaczepienia – nad ikoną jest odstęp równy
 * etykiecie, dzięki czemu znacznik jest symetryczny w pionie.
 */
@Composable
private fun VehicleMarker(m: MarkerUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(LABEL_HEIGHT + 1.dp))
        Box(Modifier.size(GLYPH + 6.dp), contentAlignment = Alignment.Center) {
            if (m.state == VehicleState.DRIVING) Pulse(m.state.color)
            Glyph(m.icon, m.state.color, m.selected, Modifier.size(if (m.selected) GLYPH + 6.dp else GLYPH))
        }
        Spacer(Modifier.height(1.dp))
        Text(
            m.label,
            Modifier.height(LABEL_HEIGHT)
                .shadow(2.dp, RoundedCornerShape(6.dp))
                .background(if (m.selected) SELECTED else Color.White, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            color = if (m.selected) Color.White else Color(0xFF212121),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dwie fale rozchodzące się od ikony jadącego pojazdu. */
@Composable
private fun Pulse(color: Color) {
    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
    Box(
        Modifier.size(GLYPH).drawBehind {
            val start = size.minDimension / 2f
            for (offset in floatArrayOf(0f, 0.5f)) {
                val p = (t + offset) % 1f
                val eased = 1f - (1f - p) * (1f - p)
                val r = start + 22.dp.toPx() * eased
                drawCircle(color.copy(alpha = (1f - p) * 0.28f), radius = r)
                drawCircle(color.copy(alpha = (1f - p) * 0.8f), radius = r, style = Stroke(2.5.dp.toPx()))
            }
        },
    )
}

/** Wektor ikony zamieniony na ścieżki (do własnego rysowania z obrysem i gradientem). */
private val pathCache = HashMap<VehicleIcon, List<Path>>()

private fun VehicleIcon.paths(): List<Path> = pathCache.getOrPut(this) { vector.toPaths() }

private fun ImageVector.toPaths(): List<Path> {
    val out = mutableListOf<Path>()
    fun walk(group: VectorGroup) {
        for (node in group) when (node) {
            is VectorPath -> if (node.fill != null) {
                out += node.pathData.toPath().apply {
                    fillType = if (node.pathFillType == PathFillType.EvenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                }
            }
            is VectorGroup -> walk(node)
        }
    }
    walk(root)
    return out
}

private val windshield by lazy { addPathNodes("M5,11l1.5,-4.5h11L19,11z").toPath() }

@Composable
private fun Glyph(icon: VehicleIcon, base: Color, selected: Boolean, modifier: Modifier) {
    val paths = icon.paths()
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val fill = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.25f), lerp(base, Color.Black, 0.15f)))
        // Biały obrys ~2,5 dp na zewnątrz kształtu (połowa grubości kreski wychodzi poza ikonę)
        val outline = Stroke(width = 2.5.dp.toPx() * 2f / s, join = StrokeJoin.Round)
        scale(s, s, pivot = Offset.Zero) {
            translate(0f, 0.5f) { paths.forEach { drawPath(it, Color.Black.copy(alpha = 0.25f), style = outline) } }
            paths.forEach { drawPath(it, if (selected) SELECTED else Color.White, style = outline) }
            paths.forEach { drawPath(it, fill) }
            if (icon == VehicleIcon.CAR) {
                // Szyba i reflektory domyślnej ikony auta
                drawPath(windshield, Color.White.copy(alpha = 0.8f))
                drawCircle(Color.White, radius = 1.5f, center = Offset(6.5f, 14.5f))
                drawCircle(Color.White, radius = 1.5f, center = Offset(17.5f, 14.5f))
            }
        }
    }
}
