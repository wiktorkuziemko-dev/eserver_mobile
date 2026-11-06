package pl.fleet.monitor.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.maxScale
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.onPathClick
import ovh.plrapps.mapcompose.api.onTap
import ovh.plrapps.mapcompose.api.removeAllMarkers
import ovh.plrapps.mapcompose.api.removeAllPaths
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.removePaths
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.setMapBackground
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.api.updatePath
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.paths.model.PatternItem
import ovh.plrapps.mapcompose.ui.state.MapState
import pl.fleet.monitor.data.RouteAnalysis
import pl.fleet.monitor.data.RoutePoint
import pl.fleet.monitor.data.RouteStop
import pl.fleet.monitor.data.speedBucket
import pl.fleet.monitor.ui.map.DEFAULT_DENSITY
import pl.fleet.monitor.ui.map.FULL_SIZE
import pl.fleet.monitor.ui.map.LEVELS
import pl.fleet.monitor.ui.map.OsmAttribution
import pl.fleet.monitor.ui.map.TILE_SIZE
import pl.fleet.monitor.ui.map.latToY
import pl.fleet.monitor.ui.map.lonToX
import pl.fleet.monitor.ui.map.osmTiles
import pl.fleet.monitor.ui.map.zoomToScale
import kotlin.math.max

// Kolory jak w analizatorze CSV
/** Prędkość: <30, <60, <90, <120, ≥120 km/h. */
internal val SPEED_COLORS = listOf(Color(0xFF2F7FD1), Color(0xFF23A17A), Color(0xFFE8A317), Color(0xFFE0661B), Color(0xFFC4283A))
internal val SPEED_LABELS = listOf("< 30", "30–60", "60–90", "90–120", "≥ 120")
internal val PARK_COLOR = Color(0xFF1F5FBF)
internal val IDLE_COLOR = Color(0xFFE0661B)
private val START_COLOR = Color(0xFF23A17A)
private val END_COLOR = Color(0xFFC4283A)
private val INK = Color(0xFF1A2028)
private val GAP_COLOR = Color(0xFF5B6571)

/** Postój z pracującym silnikiem dłużej niż 5 min – znacznik pomarańczowy. */
internal val RouteStop.isIdle: Boolean get() = idleSeconds >= 5 * 60
private val RouteStop.isLong: Boolean get() = seconds >= 3 * 3600

/** Fragment przejazdu w jednym przedziale prędkości. */
private class Segment(val id: String, val color: Color, val xy: List<Pair<Double, Double>>)

/** Stan mapy tras – trzymany w ViewModelu, żeby przetrwał obrót ekranu. */
class RouteMapState {
    private var density = DEFAULT_DENSITY

    internal val map = MapState(LEVELS, FULL_SIZE, FULL_SIZE, TILE_SIZE) {
        maxScale(2.0 * DEFAULT_DENSITY)
        magnifyingFactor(2)
        scale(zoomToScale(6.0) * DEFAULT_DENSITY)
        scroll(lonToX(19.48), latToY(52.07))
    }.apply {
        addLayer(osmTiles)
        setMapBackground(Color(0xFFEFEFEA))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Wybrana trasa – pozostałe trasy, postoje i flagi są wtedy przygaszone. */
    internal var focusTrip by mutableStateOf<Int?>(null)

    internal var drawn: RouteAnalysis? = null
    private val segments = mutableMapOf<Int, List<Segment>>()
    private var gapIds = emptyList<String>()

    internal fun updateDensity(d: Float) {
        if (d <= 0f || d == density) return
        density = d
        map.maxScale = 2.0 * d
    }

    private fun scaleFor(zoom: Double) = zoomToScale(zoom) * density

    /** Rysuje trasy (kolor wg prędkości), przerwy w danych, postoje i flagi A/B. */
    internal fun draw(a: RouteAnalysis) {
        drawn = a
        focusTrip = null
        map.removeAllPaths()
        map.removeAllMarkers()
        segments.clear()
        val p = a.points
        if (p.isEmpty()) return

        a.trips.forEachIndexed { ti, tr ->
            val list = mutableListOf<Segment>()
            var seg = mutableListOf(p[tr.s].xy())
            var cb = speedBucket(p[tr.s].speed)
            fun flush() {
                if (seg.size > 1) list += Segment("t${ti}_${list.size}", SPEED_COLORS[cb], seg)
            }
            for (k in tr.s + 1..tr.e) {
                val b = speedBucket((p[k - 1].speed + p[k].speed) / 2)
                if (b != cb) {
                    flush()
                    seg = mutableListOf(seg.last())
                    cb = b
                }
                seg += p[k].xy()
            }
            flush()
            list.forEach { s ->
                map.addPath(s.id, width = 4.dp, color = s.color.copy(alpha = 0.92f), clickable = true, zIndex = 1f) { addPoints(s.xy) }
            }
            segments[ti] = list
        }

        gapIds = a.gaps.map { g ->
            val id = "g${g.index}"
            map.addPath(
                id, width = 2.dp, color = GAP_COLOR.copy(alpha = 0.8f), zIndex = 0f,
                pattern = listOf(PatternItem.Dash(6.dp), PatternItem.Gap(8.dp)),
            ) { addPoints(listOf(p[g.index].xy(), p[g.index + 1].xy())) }
            id
        }

        a.stops.forEachIndexed { si, st ->
            map.addMarker("s$si", lonToX(st.lon), latToY(st.lat), relativeOffset = Offset(-0.5f, -0.5f), zIndex = 2f) {
                val dim = focusTrip?.let { ft -> a.trips.getOrNull(ft)?.let { st.b != it.s && st.a != it.e } } ?: false
                StopMarker(st, Modifier.alpha(if (dim) 0.2f else 1f))
            }
        }
        val first = p.first()
        val last = p.last()
        map.addMarker("A", first.x(), first.y(), relativeOffset = Offset(-0.5f, -0.5f), zIndex = 3f, clickable = false) {
            Flag("A", START_COLOR, 24.dp, Modifier.alpha(if (focusTrip != null) 0.15f else 1f))
        }
        map.addMarker("B", last.x(), last.y(), relativeOffset = Offset(-0.5f, -0.5f), zIndex = 3f, clickable = false) {
            Flag("B", END_COLOR, 24.dp, Modifier.alpha(if (focusTrip != null) 0.15f else 1f))
        }
        fit(p, maxZoom = 15.0, animate = false)
    }

    /** Wyróżnia trasę: biała obwódka, ciemny środek, kolory prędkości na wierzchu, flagi S/K. */
    internal fun focus(ti: Int?, fitToTrip: Boolean = true) {
        val a = drawn ?: return
        map.removePaths { it.startsWith("h") }
        map.removeMarker("S")
        map.removeMarker("K")
        focusTrip = ti
        val tr = ti?.let { a.trips.getOrNull(it) }
        segments.forEach { (i, list) ->
            val alpha = when {
                tr == null -> 0.92f
                i == ti -> 0f
                else -> 0.13f
            }
            list.forEach { map.updatePath(it.id, color = it.color.copy(alpha = alpha)) }
        }
        gapIds.forEach { map.updatePath(it, color = GAP_COLOR.copy(alpha = if (tr == null) 0.8f else 0.12f)) }
        if (tr == null || ti == null) return

        val p = a.points
        val xy = (tr.s..tr.e).map { p[it].xy() }
        map.addPath("h_white", width = 13.dp, color = Color.White, zIndex = 5f) { addPoints(xy) }
        map.addPath("h_ink", width = 9.dp, color = INK, zIndex = 6f) { addPoints(xy) }
        segments[ti].orEmpty().forEach { s ->
            map.addPath("h_${s.id}", width = 5.dp, color = s.color, zIndex = 7f) { addPoints(s.xy) }
        }
        map.addMarker("S", p[tr.s].x(), p[tr.s].y(), relativeOffset = Offset(-0.5f, -0.5f), zIndex = 8f, clickable = false) {
            Flag("S", START_COLOR, 28.dp)
        }
        map.addMarker("K", p[tr.e].x(), p[tr.e].y(), relativeOffset = Offset(-0.5f, -0.5f), zIndex = 8f, clickable = false) {
            Flag("K", END_COLOR, 28.dp)
        }
        if (fitToTrip) fit((tr.s..tr.e).map { p[it] }, maxZoom = 16.0, animate = true)
    }

    internal fun focusStop(st: RouteStop) {
        scope.launch { map.scrollTo(lonToX(st.lon), latToY(st.lat), max(map.scale, scaleFor(15.0))) }
    }

    private fun fit(points: List<RoutePoint>, maxZoom: Double, animate: Boolean) {
        if (points.isEmpty()) return
        val xs = points.map { it.x() }
        val ys = points.map { it.y() }
        val cx = (xs.min() + xs.max()) / 2
        val cy = (ys.min() + ys.max()) / 2
        val halfW = max(xs.max() - xs.min(), MIN_SPAN) / 2
        val halfH = max(ys.max() - ys.min(), MIN_SPAN) / 2
        val box = BoundingBox(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        scope.launch {
            val padding = Offset(0.15f, 0.2f)
            if (animate) map.scrollTo(box, padding = padding) else map.snapScrollTo(box, padding = padding)
            if (!map.scale.isFinite() || map.scale > scaleFor(maxZoom)) {
                map.scale = scaleFor(maxZoom)
                map.snapScrollTo(cx, cy)
            }
        }
    }
}

private const val MIN_SPAN = 2e-5

private fun RoutePoint.x() = lonToX(lon)
private fun RoutePoint.y() = latToY(lat)
private fun RoutePoint.xy() = x() to y()

@Composable
fun RouteMap(
    analysis: RouteAnalysis,
    state: RouteMapState,
    selectedTrip: Int?,
    onSelectTrip: (Int?) -> Unit,
    onSelectStop: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    SideEffect { state.updateDensity(density) }
    val selectTrip by rememberUpdatedState(onSelectTrip)
    val selectStop by rememberUpdatedState(onSelectStop)

    LaunchedEffect(state) {
        state.map.onPathClick { id, _, _ ->
            id.removePrefix("t").substringBefore('_').toIntOrNull()?.let { selectStop(null); selectTrip(it) }
        }
        state.map.onMarkerClick { id, _, _ ->
            id.removePrefix("s").toIntOrNull()?.let { selectTrip(null); selectStop(it) }
        }
        state.map.onTap { _, _ -> selectTrip(null); selectStop(null) }
    }
    LaunchedEffect(analysis) {
        if (state.drawn !== analysis) state.draw(analysis)
    }
    LaunchedEffect(analysis, selectedTrip) {
        if (state.focusTrip != selectedTrip) state.focus(selectedTrip)
    }

    Box(modifier) {
        MapUI(Modifier.matchParentSize(), state = state.map)
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

/** Znacznik postoju: niebieski kwadrat „P” (pomarańczowy, gdy silnik pracował), większy dla postojów ≥ 3 h. */
@Composable
internal fun StopMarker(st: RouteStop, modifier: Modifier = Modifier) {
    val size: Dp = if (st.isLong) 26.dp else 22.dp
    Box(
        modifier
            .size(size)
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .background(if (st.isIdle) IDLE_COLOR else PARK_COLOR, RoundedCornerShape(4.dp))
            .border(2.dp, Color.White, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("P", color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (st.isLong) 15.sp else 13.sp)
    }
}

@Composable
private fun Flag(text: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (size > 24.dp) 14.sp else 12.sp)
    }
}
