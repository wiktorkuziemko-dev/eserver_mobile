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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import ovh.plrapps.mapcompose.api.addClusterer
import ovh.plrapps.mapcompose.api.maxScale
import ovh.plrapps.mapcompose.api.removeClusterer
import ovh.plrapps.mapcompose.api.setClustererExemptList
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.ui.state.markers.model.RenderingStrategy
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.platformTileHttpClient
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

private val tileClient by lazy { platformTileHttpClient() }

/** Pamięć podręczna kafelków na dysku – czyszczona przy pierwszym użyciu, jeśli za duża lub za stara. */
private val tileCache: TileCache? by lazy {
    AppGraph.takeIf { it.isInitialized }?.container?.cacheDir?.let { TileCache(it) }?.also { it.prune() }
}

/** Kafelki: najpierw z dysku, potem z serwera OSM (wymagany identyfikujący User-Agent). */
private val osmTiles = TileStreamProvider { row, col, zoom ->
    tileCache?.read(zoom, col, row)?.let { return@TileStreamProvider Buffer().apply { write(it) } }
    try {
        val r = tileClient.get("https://tile.openstreetmap.org/$zoom/$col/$row.png") {
            header(HttpHeaders.UserAgent, "E-SERVER-mini (pl.fleet.monitor)")
        }
        if (r.status.isSuccess()) {
            val bytes = r.readRawBytes()
            tileCache?.write(zoom, col, row, bytes)
            Buffer().apply { write(bytes) }
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

private const val CLUSTER_ID = "vehicles"

/** Gęstość ekranu przyjmowana przed pierwszym pomiarem (typowy telefon). */
private const val DEFAULT_DENSITY = 2.75f

/** Dane znacznika – zmiana wartości odświeża tylko ten znacznik. */
internal data class MarkerUi(val label: String, val state: VehicleState, val icon: VehicleIcon, val selected: Boolean)

/** Stan mapy (pozycja i przybliżenie) – przeżywa przełączanie zakładek, bo trzyma go ViewModel. */
class FleetMapState {
    /**
     * Gęstość ekranu. Kafelki 256 px wyświetlamy w punktach ekranu (dp), a nie w pikselach –
     * jak wcześniej osmdroid – dzięki czemu pobieranych jest kilka razy mniej kafelków,
     * a napisy na mapie są czytelne.
     */
    private var density = DEFAULT_DENSITY

    internal val map = MapState(LEVELS, FULL_SIZE, FULL_SIZE, TILE_SIZE) {
        maxScale(2.0 * DEFAULT_DENSITY)
        // Kafelek z poziomu niższego powiększony 2–4 razy (zamiast wyższego poziomu pomniejszonego).
        magnifyingFactor(2)
        scale(zoomToScale(6.0) * DEFAULT_DENSITY)
        scroll(lonToX(19.48), latToY(52.07)) // domyślnie środek Polski
    }.apply {
        addLayer(osmTiles)
        setMapBackground(Color(0xFFEFEFEA))
    }

    internal val markerData = mutableStateMapOf<String, MarkerUi>()

    /** Mnożnik wielkości ikon i opisów (suwak w ustawieniach). */
    internal var markerScale by mutableFloatStateOf(1f)

    /** Czy znaczniki są obecnie dodane z grupowaniem (null = jeszcze nie dodane). */
    internal var clusteringApplied: Boolean? = null
    internal var exempt: Set<String> = emptySet()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Czy widok był już dopasowany do pojazdów (żeby nie nadpisywać przybliżenia ustawionego przez użytkownika). */
    var hasFitted = false

    private fun scaleFor(zoom: Double) = zoomToScale(zoom) * density

    internal fun updateDensity(d: Float) {
        if (d <= 0f || d == density) return
        density = d
        map.maxScale = 2.0 * d
    }

    /** Dopasowuje widok do pojazdów; [animate] = false przy pierwszym wyświetleniu (bez przelotu nad mapą). */
    fun fitAll(vehicles: List<Vehicle>, animate: Boolean = true) {
        val points = vehicles.filter { it.hasPosition }.map { lonToX(it.longitude!!) to latToY(it.latitude!!) }
        if (points.isEmpty()) return
        hasFitted = true
        scope.launch {
            if (points.size == 1) {
                val (x, y) = points[0]
                if (animate) {
                    map.scrollTo(x, y, scaleFor(15.0))
                } else {
                    map.scale = scaleFor(15.0)
                    map.snapScrollTo(x, y)
                }
            } else {
                val box = BoundingBox(
                    xLeft = points.minOf { it.first }, yTop = points.minOf { it.second },
                    xRight = points.maxOf { it.first }, yBottom = points.maxOf { it.second },
                )
                if (animate) map.scrollTo(box, padding = Offset(0.3f, 0.45f)) else map.snapScrollTo(box, padding = Offset(0.3f, 0.45f))
                if (map.scale > scaleFor(16.0)) map.scale = scaleFor(16.0)
            }
        }
    }

    /** Śledzenie: przesuwa mapę na nową pozycję pojazdu bez zmiany przybliżenia. */
    fun follow(v: Vehicle) {
        if (!v.hasPosition) return
        scope.launch { map.scrollTo(lonToX(v.longitude!!), latToY(v.latitude!!), map.scale) }
    }

    fun focusOn(v: Vehicle, zoom: Double = 15.0) {
        if (!v.hasPosition) return
        hasFitted = true
        scope.launch { map.scrollTo(lonToX(v.longitude!!), latToY(v.latitude!!), max(scaleFor(zoom), map.scale)) }
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
    clusterMarkers: Boolean = false,
    markerScale: Float = 1f,
    onSelect: (Vehicle?) -> Unit = {},
) {
    val density = LocalDensity.current.density
    SideEffect {
        state.updateDensity(density)
        state.markerScale = markerScale
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentVehicles by rememberUpdatedState(vehicles)

    LaunchedEffect(state) {
        state.map.onMarkerClick { id, _, _ -> currentVehicles.firstOrNull { it.deviceId == id }?.let { currentOnSelect(it) } }
        state.map.onTap { _, _ -> currentOnSelect(null) }
    }

    // Synchronizacja znaczników z listą pojazdów
    LaunchedEffect(vehicles, selectedId, icons, labels, clusterMarkers) {
        // Zmiana trybu grupowania = przebudowa wszystkich znaczników
        if (state.clusteringApplied != clusterMarkers) {
            state.markerData.keys.toList().forEach { state.map.removeMarker(it) }
            state.markerData.clear()
            if (state.clusteringApplied == true) state.map.removeClusterer(CLUSTER_ID)
            if (clusterMarkers) {
                state.map.addClusterer(CLUSTER_ID, clusteringThreshold = 56.dp) { ids -> { ClusterMarker(ids, state) } }
            }
            state.exempt = emptySet()
            state.clusteringApplied = clusterMarkers
        }
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
                state.map.addMarker(
                    id, x, y,
                    relativeOffset = Offset(-0.5f, -0.5f),
                    renderingStrategy = if (clusterMarkers) RenderingStrategy.Clustering(CLUSTER_ID) else RenderingStrategy.Default,
                ) {
                    state.markerData[id]?.let { VehicleMarker(it, state.markerScale) }
                }
            }
            state.markerData[v.deviceId] = ui
            state.map.updateMarkerZ(v.deviceId, if (selected) 2f else if (v.state == VehicleState.DRIVING) 1f else 0f)
        }
        // Zaznaczony pojazd nigdy nie trafia do grupy – zawsze jest widoczny.
        if (clusterMarkers) {
            val exempt = setOfNotNull(selectedId?.takeIf { it in ids })
            if (exempt != state.exempt) {
                state.map.setClustererExemptList(CLUSTER_ID, exempt)
                state.exempt = exempt
            }
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
private fun VehicleMarker(m: MarkerUi, scale: Float = 1f) {
    val glyph = GLYPH * scale
    val labelHeight = LABEL_HEIGHT * scale
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(labelHeight + 1.dp))
        Box(Modifier.size(glyph + 6.dp * scale), contentAlignment = Alignment.Center) {
            if (m.state == VehicleState.DRIVING) Pulse(m.state.color, glyph, scale)
            Glyph(m.icon, m.state.color, m.selected, Modifier.size(if (m.selected) glyph + 6.dp * scale else glyph))
        }
        Spacer(Modifier.height(1.dp))
        Text(
            m.label,
            Modifier.height(labelHeight)
                .shadow(2.dp, RoundedCornerShape(6.dp * scale))
                .background(if (m.selected) SELECTED else Color.White, RoundedCornerShape(6.dp * scale))
                .padding(horizontal = 7.dp * scale, vertical = 2.dp * scale),
            color = if (m.selected) Color.White else Color(0xFF212121),
            fontSize = (11 * scale).sp,
            lineHeight = (13 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dwie fale rozchodzące się od ikony jadącego pojazdu (lub grupy z jadącym pojazdem). */
@Composable
private fun Pulse(color: Color, size: Dp, scale: Float) {
    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
    Box(
        Modifier.size(size).drawBehind {
            val start = this.size.minDimension / 2f
            for (offset in floatArrayOf(0f, 0.5f)) {
                val p = (t + offset) % 1f
                val eased = 1f - (1f - p) * (1f - p)
                val r = start + 22.dp.toPx() * scale * eased
                drawCircle(color.copy(alpha = (1f - p) * 0.28f), radius = r)
                drawCircle(color.copy(alpha = (1f - p) * 0.8f), radius = r, style = Stroke(2.5.dp.toPx() * scale))
            }
        },
    )
}

/**
 * Grupa pojazdów: biała plakietka z liczbą, otoczona pierścieniem podzielonym proporcjonalnie
 * na jadące (zielony) i zaparkowane (pomarańczowy). Pulsuje, gdy w grupie jest jadący pojazd.
 * Dotknięcie grupy przybliża mapę (domyślne zachowanie biblioteki).
 */
@Composable
private fun ClusterMarker(ids: List<String>, state: FleetMapState) {
    val scale = state.markerScale
    val items = ids.mapNotNull { state.markerData[it] }
    val driving = items.count { it.state == VehicleState.DRIVING }
    val total = ids.size
    val size = (if (total >= 100) 52.dp else if (total >= 10) 46.dp else 42.dp) * scale
    Box(Modifier.size(size + 8.dp * scale), contentAlignment = Alignment.Center) {
        if (driving > 0) Pulse(VehicleState.DRIVING.color, size, scale)
        Canvas(Modifier.size(size).shadow(4.dp * scale, CircleShape)) {
            val ring = 5.dp.toPx() * scale
            drawCircle(Color.White)
            val arcSize = Size(this.size.width - ring, this.size.height - ring)
            val topLeft = Offset(ring / 2f, ring / 2f)
            val drivingSweep = if (items.isEmpty()) 0f else 360f * driving / items.size
            if (drivingSweep < 360f) {
                drawArc(VehicleState.PARKED.color, -90f + drivingSweep, 360f - drivingSweep, false, topLeft, arcSize, style = Stroke(ring))
            }
            if (drivingSweep > 0f) {
                drawArc(VehicleState.DRIVING.color, -90f, drivingSweep, false, topLeft, arcSize, style = Stroke(ring))
            }
        }
        Text(
            total.toString(),
            color = Color(0xFF212121),
            fontSize = (15 * scale).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Przykładowy znacznik do podglądu wielkości w ustawieniach. */
@Composable
fun MarkerPreview(scale: Float, label: String = "Ford Focus") {
    VehicleMarker(MarkerUi(label, VehicleState.DRIVING, VehicleIcon.DEFAULT, selected = false), scale)
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
