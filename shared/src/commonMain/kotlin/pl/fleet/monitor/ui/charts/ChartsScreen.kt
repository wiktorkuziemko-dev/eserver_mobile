package pl.fleet.monitor.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.data.pad2
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.components.GradientTopBar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Instant

// Jedna seria na wykres (bez dwóch osi Y) – kolory odróżniają wykresy, wartości i opisy są w kolorze tekstu.
private val SPEED_COLOR = Color(0xFF2F7FD1)
private val RPM_COLOR = Color(0xFF7A4FC9)
private val VOLTAGE_COLOR = Color(0xFF1F8A66)
private val FUEL_COLOR = Color(0xFFD9651B)

/** Luka w danych dłuższa niż 15 min przerywa linię (na postoju nadajnik raportuje rzadko). */
private const val GAP_SECONDS = 15 * 60

@Composable
fun ChartsScreen(vehicleName: String, title: String, vm: ChartsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = { GradientTopBar(title = title, subtitle = vehicleName, onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::load) { Text("Spróbuj ponownie") }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (ui.cursor != null) "Wybrana chwila: ${hms(ui.cursor!!)} – dotknij wykresu, aby zmienić"
                        else "Dotknij wykresu lub przesuń palcem, aby odczytać wartości w danej chwili",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChartCard("Prędkość", "km/h", 0, ui.speed, SPEED_COLOR, vm.from, vm.to, ui.cursor, vm::setCursor, zeroBased = true)
                    ChartCard("Obroty silnika", "obr/min", 0, ui.rpm, RPM_COLOR, vm.from, vm.to, ui.cursor, vm::setCursor, zeroBased = true)
                    ChartCard("Napięcie akumulatora", "V", 2, ui.voltage, VOLTAGE_COLOR, vm.from, vm.to, ui.cursor, vm::setCursor, zeroBased = false)
                    ChartCard("Paliwo", "l", 1, ui.fuel, FUEL_COLOR, vm.from, vm.to, ui.cursor, vm::setCursor, zeroBased = false)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    unit: String,
    decimals: Int,
    series: Series,
    color: Color,
    from: Instant,
    to: Instant,
    cursor: Instant?,
    onCursor: (Instant?) -> Unit,
    zeroBased: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 3.dp).padding(0.dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(color) }
                }
                Spacer(Modifier.width(8.dp))
                Text("$title [$unit]", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // Wartość w wybranej chwili (ostatni raport przed nią)
                val v = cursor?.let { c -> series.points.lastOrNull { it.first <= c && it.second != null }?.second }
                if (v != null) Text("${Fmt.decimal(v, decimals)} $unit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (!series.hasData) {
                Text(
                    "Brak danych z nadajnika dla tego okresu",
                    Modifier.padding(vertical = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val values = series.points.mapNotNull { it.second }
            val (lo, hi) = axisRange(values.min(), values.max(), zeroBased)
            Spacer(Modifier.height(6.dp))
            Row {
                // Oś Y: wartość maksymalna, środkowa i minimalna
                Column(Modifier.width(42.dp).height(130.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf(hi, (lo + hi) / 2, lo).forEach {
                        Text(Fmt.decimal(it, if (hi - lo < 5) 1 else 0), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LineChart(series, color, from, to, lo, hi, cursor, onCursor, Modifier.weight(1f).height(130.dp))
            }
            // Oś X: początek, środek i koniec okresu
            Row(Modifier.fillMaxWidth().padding(start = 42.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(from, from + (to - from) / 2, to).forEach {
                    Text(hm(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LineChart(
    series: Series,
    color: Color,
    from: Instant,
    to: Instant,
    lo: Double,
    hi: Double,
    cursor: Instant?,
    onCursor: (Instant?) -> Unit,
    modifier: Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val total = (to - from).inWholeMilliseconds.coerceAtLeast(1)
    fun timeAt(x: Float, width: Float): Instant = from + (to - from) * (x / width).coerceIn(0f, 1f).toDouble()
    Canvas(
        modifier
            .pointerInput(from, to) { detectTapGestures { onCursor(timeAt(it.x, size.width.toFloat())) } }
            .pointerInput(from, to) {
                detectHorizontalDragGestures { change, _ -> onCursor(timeAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val w = size.width
        val h = size.height
        fun x(t: Instant) = ((t - from).inWholeMilliseconds.toFloat() / total) * w
        fun y(v: Double) = (h - ((v - lo) / (hi - lo)).toFloat() * h)
        // Delikatna siatka: góra, środek, dół
        listOf(0f, h / 2, h).forEach { gy ->
            drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx(),
                pathEffect = if (gy == h) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
        // Linia 2 dp; przerwy w danych i brak odczytu dzielą ją na odcinki.
        val path = Path()
        var open = false
        var prev: Instant? = null
        for ((t, v) in series.points) {
            if (v == null) { open = false; continue }
            val px = x(t)
            val py = y(v)
            val gap = prev?.let { (t - it).inWholeSeconds > GAP_SECONDS } ?: true
            if (!open || gap) path.moveTo(px, py) else path.lineTo(px, py)
            open = true
            prev = t
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Kursor: pionowa linia i punkt na serii
        cursor?.let { c ->
            val cx = x(c)
            drawLine(ink.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.dp.toPx())
            series.points.lastOrNull { it.first <= c && it.second != null }?.let { (_, v) ->
                val cy = y(v!!)
                drawCircle(Color.White, 5.dp.toPx(), Offset(cx, cy))
                drawCircle(color, 3.5.dp.toPx(), Offset(cx, cy))
            }
        }
    }
}

/** Zakres osi Y z małym marginesem; prędkość i obroty od zera. */
private fun axisRange(min: Double, max: Double, zeroBased: Boolean): Pair<Double, Double> {
    val lo0 = if (zeroBased) 0.0 else min
    var hi0 = max
    if (hi0 - lo0 < 1e-6) hi0 = lo0 + 1.0
    val pad = (hi0 - lo0) * 0.08
    val lo = if (zeroBased) 0.0 else floor((lo0 - pad) * 10) / 10
    val hi = ceil((hi0 + pad) * 10) / 10
    return lo to hi
}

private fun hm(t: Instant) = Fmt.local(t).let { "${pad2(it.hour)}:${pad2(it.minute)}" }
private fun hms(t: Instant) = Fmt.local(t).let { "${pad2(it.hour)}:${pad2(it.minute)}:${pad2(it.second)}" }
