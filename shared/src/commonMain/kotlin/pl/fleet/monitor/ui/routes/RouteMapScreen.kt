package pl.fleet.monitor.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.data.RouteAnalysis
import pl.fleet.monitor.data.RouteStop
import pl.fleet.monitor.data.RouteTrip
import pl.fleet.monitor.data.pad2
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.components.GradientTopBar
import pl.fleet.monitor.ui.history.DateRangeDialog
import pl.fleet.monitor.ui.history.RangePreset
import kotlin.time.Instant

@Composable
fun RouteMapScreen(vehicleLabel: String, vm: RouteMapViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GradientTopBar(title = "Trasy na mapie", subtitle = vehicleLabel, onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ROUTE_PRESETS.forEach { p ->
                    FilterChip(selected = ui.preset == p, onClick = { vm.selectPreset(p) }, label = { Text(p.label) })
                }
                FilterChip(
                    selected = ui.preset == RangePreset.CUSTOM,
                    onClick = { showPicker = true },
                    label = { Text(RangePreset.CUSTOM.label) },
                    leadingIcon = { Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp)) },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (ui.from == ui.to) Fmt.dayHeader(ui.from) else "${Fmt.date(ui.from)} – ${Fmt.date(ui.to)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ui.analysis?.takeIf { it.points.isNotEmpty() }?.let { a ->
                    Text(
                        "${Fmt.km(a.km)} · jazda ${Fmt.duration(a.driveSeconds)} · ${a.trips.size} ${plural(a.trips.size, "trasa", "trasy", "tras")} · " +
                            "${a.stops.size} ${plural(a.stops.size, "postój", "postoje", "postojów")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) else Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val analysis = ui.analysis ?: RouteAnalysis.EMPTY
                RouteMap(
                    analysis = analysis,
                    state = vm.mapState,
                    selectedTrip = ui.selectedTrip,
                    onSelectTrip = vm::selectTrip,
                    onSelectStop = vm::selectStop,
                    modifier = Modifier.fillMaxSize(),
                )
                Legend(Modifier.align(Alignment.TopEnd).padding(10.dp))

                val error = ui.error
                when {
                    error != null -> InfoBubble("Nie udało się pobrać tras", error, Modifier.align(Alignment.Center), onRetry = vm::load)
                    !ui.loading && ui.analysis != null && analysis.points.isEmpty() -> InfoBubble(
                        "Brak tras w tym okresie",
                        "Pojazd nie wysłał pozycji. Wybierz inny zakres dat.",
                        Modifier.align(Alignment.Center),
                    )
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                    val trip = ui.selectedTrip?.let { analysis.trips.getOrNull(it) }
                    val stop = ui.selectedStop?.let { analysis.stops.getOrNull(it) }
                    when {
                        trip != null -> TripCard(
                            trip, ui.selectedTrip!!, analysis.trips.size,
                            onPrev = { vm.stepTrip(-1) }, onNext = { vm.stepTrip(1) }, onClose = { vm.selectTrip(null) },
                        )
                        stop != null -> StopCard(stop, ui.selectedStop!!, onClose = { vm.selectStop(null) })
                        analysis.trips.isNotEmpty() -> HintPill(
                            "Dotknij trasy lub znacznika P, aby zobaczyć szczegóły",
                            onClick = { vm.stepTrip(1) },
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        DateRangeDialog(ui.from, ui.to, MAX_ROUTE_DAYS, onDismiss = { showPicker = false }) { start, end ->
            showPicker = false
            vm.setCustomRange(start, end)
        }
    }
}

/** Pasek wybranej trasy (jak „focus bar” w analizatorze): czas, długość, prędkości, adresy, przewijanie tras. */
@Composable
private fun TripCard(tr: RouteTrip, index: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzednia trasa") }
                Column(Modifier.weight(1f)) {
                    Text("Trasa ${index + 1} z $count · ${Fmt.km(tr.km)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${Fmt.dateTime(tr.start)} – ${hm(tr.end)} · ${Fmt.duration(tr.seconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, enabled = index < count - 1) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następna trasa") }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Zamknij") }
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "Średnio ${Fmt.decimal(tr.avgSpeed, 0)} km/h · maks. ${Fmt.decimal(tr.maxSpeed, 0)} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (tr.from.isNotEmpty() || tr.to.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${tr.from.ifEmpty { "?" }} → ${tr.to.ifEmpty { "?" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopCard(st: RouteStop, index: Int, onClose: () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StopMarker(st)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Postój ${index + 1} · ${Fmt.duration(st.seconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Zamknij") }
            }
            Column(Modifier.padding(end = 12.dp)) {
                Text("Od ${Fmt.dateTime(st.start)} do ${Fmt.dateTime(st.end)}", style = MaterialTheme.typography.bodyMedium)
                if (st.idleSeconds > 0) {
                    Text(
                        "Silnik pracował: ${Fmt.duration(st.idleSeconds)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (st.isIdle) IDLE_COLOR else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (st.address.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(st.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Legenda: kolory prędkości i znaczniki postojów; dotknięcie zwija ją do samego nagłówka. */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        modifier.clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("km/h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (expanded) {
                SPEED_COLORS.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 18.dp, height = 4.dp).background(c, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(SPEED_LABELS[i], style = MaterialTheme.typography.labelSmall)
                    }
                }
                LegendStop(PARK_COLOR, "postój")
                LegendStop(IDLE_COLOR, "silnik pracował")
            }
        }
    }
}

@Composable
private fun LegendStop(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(color, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Text("P", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HintPill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFF1A2028),
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InfoBubble(title: String, text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Card(modifier.padding(24.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) androidx.compose.material3.TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

private fun hm(t: Instant): String = Fmt.local(t).let { "${pad2(it.hour)}:${pad2(it.minute)}" }

/** Polska odmiana liczebnika: 1 trasa, 2–4 trasy, 5 tras. */
private fun plural(n: Int, one: String, few: String, many: String): String = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}
