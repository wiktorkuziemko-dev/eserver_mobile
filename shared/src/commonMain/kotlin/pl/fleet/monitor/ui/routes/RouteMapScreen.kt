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
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.vector.ImageVector
import pl.fleet.monitor.data.RoutePause
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import pl.fleet.monitor.data.speedBucket
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
    var showTrips by remember { mutableStateOf(false) }

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
                    Spacer(Modifier.height(6.dp))
                    TripSelector(a, ui.selectedTrip, onClick = { showTrips = true })
                }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) else Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val analysis = ui.analysis ?: RouteAnalysis.EMPTY
                RouteMap(
                    analysis = analysis,
                    state = vm.mapState,
                    selectedTrip = ui.selectedTrip,
                    fitOnSelect = ui.fitOnSelect,
                    onSelectTrip = vm::selectTrip,
                    onSelectEvent = vm::selectEvent,
                    modifier = Modifier.fillMaxSize(),
                )
                if (vm.showLegend) Legend(Modifier.align(Alignment.TopEnd).padding(10.dp))

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
                    val event = ui.selectedEvent
                    val player = ui.player
                    when {
                        event != null -> EventCard(analysis, event, vm.mapState.pauses, onClose = { vm.selectEvent(null) })
                        player.active -> PlayerBar(ui, trip, vm)
                        trip != null -> {
                            TripCard(
                                trip, ui.selectedTrip!!, analysis.trips.size,
                                onPrev = { vm.stepTrip(-1) }, onNext = { vm.stepTrip(1) }, onClose = { vm.selectTrip(null) },
                            )
                            // Animacja jest dostępna dopiero po wybraniu konkretnej trasy.
                            Spacer(Modifier.height(8.dp))
                            PlayButton("Odtwórz przejazd tej trasy", onClick = vm::togglePlay)
                        }
                        // Bez wybranej trasy – animacja całego widocznego zakresu.
                        analysis.trips.isNotEmpty() -> PlayButton("Odtwórz cały zakres", onClick = vm::togglePlay)
                    }
                }
            }
        }
    }

    if (showTrips) {
        ui.analysis?.let { a ->
            TripListSheet(a, ui.selectedTrip, onDismiss = { showTrips = false }) { i ->
                showTrips = false
                vm.selectTrip(i)
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
private fun EventCard(a: RouteAnalysis, e: MapEvent, pauses: List<RoutePause>, onClose: () -> Unit) {
    when (e) {
        is MapEvent.Stop -> a.stops.getOrNull(e.index)?.let { st ->
            DetailsCard({ StopMarker(st) }, "Postój ${e.index + 1} · ${Fmt.duration(st.seconds)}", onClose) {
                Text("Od ${Fmt.dateTime(st.start)} do ${Fmt.dateTime(st.end)}", style = MaterialTheme.typography.bodyMedium)
                IdleLine(st)
                AddressLine(st.address)
            }
        }
        is MapEvent.TripStart -> a.trips.getOrNull(e.trip)?.let { tr ->
            val before = a.stops.firstOrNull { it.b == tr.s }
            DetailsCard({ EventIcon(Icons.Filled.Key, START_COLOR, parked = before != null, size = 26.dp) }, "Start trasy ${e.trip + 1}", onClose) {
                Text("Wyjazd: ${Fmt.dateTime(tr.start)}", style = MaterialTheme.typography.bodyMedium)
                if (before != null) {
                    Text("Wcześniej postój ${Fmt.duration(before.seconds)} (od ${Fmt.dateTime(before.start)})", style = MaterialTheme.typography.bodyMedium)
                    IdleLine(before)
                }
                AddressLine(tr.from)
            }
        }
        is MapEvent.TripEnd -> a.trips.getOrNull(e.trip)?.let { tr ->
            val after = a.stops.firstOrNull { it.a == tr.e }
            DetailsCard({ EventIcon(Icons.Filled.SportsScore, INK, parked = after != null, size = 26.dp) }, "Koniec trasy ${e.trip + 1}", onClose) {
                Text("Przyjazd: ${Fmt.dateTime(tr.end)}", style = MaterialTheme.typography.bodyMedium)
                Text("Przejechano ${Fmt.km(tr.km)} w ${Fmt.duration(tr.seconds)}", style = MaterialTheme.typography.bodyMedium)
                if (after != null) {
                    Text("Następnie postój ${Fmt.duration(after.seconds)} (do ${Fmt.dateTime(after.end)})", style = MaterialTheme.typography.bodyMedium)
                    IdleLine(after)
                }
                AddressLine(tr.to)
            }
        }
        is MapEvent.Pause -> pauses.getOrNull(e.index)?.let { pz ->
            DetailsCard({ PauseMarker() }, "Postój na trasie · ${Fmt.duration(pz.seconds)}", onClose) {
                Text("Od ${hms(pz.start)} do ${hms(pz.end)}", style = MaterialTheme.typography.bodyMedium)
                AddressLine(pz.address)
            }
        }
    }
}

@Composable
private fun DetailsCard(icon: @Composable () -> Unit, title: String, onClose: () -> Unit, body: @Composable () -> Unit) {
    Card(elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Zamknij") }
            }
            Column(Modifier.padding(end = 12.dp)) { body() }
        }
    }
}

@Composable
private fun IdleLine(st: RouteStop) {
    if (st.idleSeconds > 0) {
        Text(
            "Silnik pracował: ${Fmt.duration(st.idleSeconds)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (st.isIdle) IDLE_COLOR else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AddressLine(address: String) {
    if (address.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
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

private val ACCENT = Color(0xFFF0AD1C)
private val ON_ACCENT = Color(0xFF1A1400)
private val INK = Color(0xFF1A2028)

@Composable
private fun PlayButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = INK,
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(ACCENT, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = ON_ACCENT)
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
}

/** Odtwarzacz wybranej trasy: zegar, prędkość, dystans, zapłon, paliwo i adres zmieniające się w czasie. */
@Composable
private fun PlayerBar(ui: RouteMapUiState, tr: RouteTrip?, vm: RouteMapViewModel) {
    val p = ui.player
    val s = ui.sample
    val a = ui.analysis ?: return
    val index = ui.selectedTrip ?: 0
    // Zakres animacji: wybrana trasa albo cały okres
    val start = tr?.start ?: a.points.firstOrNull()?.time ?: return
    val end = tr?.end ?: a.points.last().time
    val totalKm = tr?.km ?: a.km
    Card(elevation = CardDefaults.cardElevation(6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = vm::togglePlay, shape = CircleShape, color = ACCENT, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (p.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (p.playing) "Pauza" else "Odtwórz", tint = ON_ACCENT)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.let { hms(it.time) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (tr != null) "Trasa ${index + 1} · ${Fmt.dateTime(tr.start)} – ${hm(tr.end)}"
                        else "Cały zakres · ${s?.trip?.let { "trasa ${it + 1}" } ?: "postój"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s?.let { Fmt.decimal(it.speed, 0) } ?: "–"} km/h", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = s?.let { SPEED_COLORS[speedBucket(it.speed)] } ?: Color.Unspecified)
                    // Dystans od początku tej trasy
                    Text(if (s != null) "${Fmt.km(p.km)} z ${Fmt.km(totalKm)}" else "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::closePlayer) { Icon(Icons.Outlined.Close, "Zamknij odtwarzanie") }
            }
            val total = (end - start).inWholeMilliseconds
            Slider(
                value = p.time?.takeIf { total > 0 }?.let { ((it - start).inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f) } ?: 0f,
                onValueChange = vm::seekFraction,
                modifier = Modifier.padding(end = 8.dp).height(32.dp),
                colors = SliderDefaults.colors(thumbColor = ACCENT, activeTrackColor = ACCENT),
            )
            if (s != null) {
                val extras = listOfNotNull(
                    s.ignition?.let { if (it) "zapłon wł." else "zapłon wył." },
                    s.fuel?.takeIf { it > 0 }?.let { "paliwo ${Fmt.decimal(it, 1)} l" },
                ).joinToString(" · ")
                if (extras.isNotEmpty()) Text(extras, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (s.address.isNotEmpty()) {
                    Text(s.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
                }
            }
            // Tempo: wolno / szybko / ultra
            Row(Modifier.padding(top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    PlaybackSpeed.entries.forEachIndexed { i, sp ->
                        SegmentedButton(
                            selected = p.speed == sp,
                            onClick = { vm.setSpeed(sp) },
                            shape = SegmentedButtonDefaults.itemShape(i, PlaybackSpeed.entries.size),
                            icon = {},
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(sp.icon(), null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(sp.label, maxLines = 1)
                                }
                            },
                        )
                    }
                }
            }
            // Śledzenie: mapa przesuwa się za pojazdem przy dowolnym przybliżeniu ustawionym przez użytkownika.
            Row(Modifier.padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Śledź pojazd", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = p.follow, onCheckedChange = { vm.toggleFollow() })
            }
        }
    }
}

/** Ikona tempa: odtwarzanie, przewijanie, rakieta. */
private fun PlaybackSpeed.icon(): ImageVector = when (this) {
    PlaybackSpeed.SLOW -> Icons.Filled.PlayArrow
    PlaybackSpeed.FAST -> Icons.Filled.FastForward
    PlaybackSpeed.ULTRA -> Icons.Filled.RocketLaunch
}

/** Przycisk wyboru trasy: „Wszystkie trasy (30)” albo opis wybranej trasy; otwiera listę tras. */
@Composable
private fun TripSelector(a: RouteAnalysis, selected: Int?, onClick: () -> Unit) {
    val tr = selected?.let { a.trips.getOrNull(it) }
    Surface(
        onClick = onClick,
        enabled = a.trips.isNotEmpty(),
        shape = RoundedCornerShape(12.dp),
        color = if (tr != null) Color(0xFFFDECEA) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Route, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    a.trips.isEmpty() -> "Brak tras w tym okresie"
                    tr == null -> "Wszystkie trasy (${a.trips.size}) – wybierz, aby pokazać jedną"
                    else -> "Trasa ${(selected ?: 0) + 1}: ${Fmt.dateTime(tr.start)}–${hm(tr.end)} · ${Fmt.km(tr.km)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (tr != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, null)
        }
    }
}

/** Lista zrealizowanych tras (pogrupowana po dniach) – wybór pokazuje na mapie tylko tę trasę. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripListSheet(a: RouteAnalysis, selected: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                TripRow(
                    title = "Wszystkie trasy",
                    subtitle = "${a.trips.size} ${plural(a.trips.size, "trasa", "trasy", "tras")} · ${Fmt.km(a.km)}",
                    selected = selected == null,
                    onClick = { onPick(null) },
                )
            }
            a.trips.withIndex().groupBy { Fmt.local(it.value.start).date }.forEach { (day, trips) ->
                item {
                    Text(
                        Fmt.dayHeader(day),
                        Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(trips.size) { j ->
                    val (i, tr) = trips[j]
                    TripRow(
                        title = "Trasa ${i + 1} · ${hm(tr.start)}–${hm(tr.end)} · ${Fmt.km(tr.km)}",
                        subtitle = listOf(shortAddress(tr.from), shortAddress(tr.to)).filter { it.isNotEmpty() }.joinToString(" → ")
                            .ifEmpty { Fmt.duration(tr.seconds) },
                        selected = selected == i,
                        onClick = { onPick(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TripRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) Color(0xFFFDECEA) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Krótki adres: ulica z numerem i miejscowość (API podaje „34, Adama Kawika, …, Zabrze, …, Polska”). */
private fun shortAddress(full: String): String {
    val parts = full.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return full
    val street = if (parts[0].any { it.isDigit() } && parts[0].length <= 6) "${parts[1]} ${parts[0]}" else parts[0]
    return street
}

private fun hms(t: Instant): String = Fmt.local(t).let { "${pad2(it.hour)}:${pad2(it.minute)}:${pad2(it.second)}" }

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
