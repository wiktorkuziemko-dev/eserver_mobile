package pl.fleet.monitor.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.FleetUiState
import pl.fleet.monitor.ui.FleetViewModel
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.components.StateBadge
import pl.fleet.monitor.ui.color
import pl.fleet.monitor.ui.label
import pl.fleet.monitor.ui.shortLabel

@Composable
fun MapScreen(
    vm: FleetViewModel,
    ui: FleetUiState,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRoutes: (String) -> Unit = {},
) {
    val visible = ui.mapVehicles
    // Mapa ma być stale widoczna (np. w trybie śledzenia) – bez wygaszania ekranu.
    pl.fleet.monitor.platform.KeepScreenOn(ui.keepScreenOn)

    LaunchedEffect(ui.vehicles.isNotEmpty()) {
        // Pierwsze wyświetlenie: od razu nad pojazdami, bez animowanego przelotu (mniej pobieranych kafelków).
        if (ui.vehicles.isNotEmpty() && !vm.mapState.hasFitted) vm.mapState.fitAll(visible.ifEmpty { ui.vehicles }, animate = false)
    }

    Box(modifier.fillMaxSize()) {
        FleetMap(
            vehicles = visible,
            modifier = Modifier.fillMaxSize(),
            state = vm.mapState,
            selectedId = ui.selectedId,
            followId = ui.followId,
            icons = ui.icons,
            labels = ui.customLabels,
            clusterMarkers = ui.clusterMarkers,
            markerScale = ui.markerScale,
            onSelect = vm::select,
        )

        // Kapsuła segmentowa „Twoje pojazdy” + pigułka odświeżania – jeden rząd 44 dp
        Column(Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth()) {
            // Pigułka z godziną ma pierwszeństwo – kapsuła dostaje resztę miejsca i w razie potrzeby skraca opisy.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.weight(1f, fill = false).height(44.dp).semantics { contentDescription = "Twoje pojazdy – filtr mapy" },
                ) {
                    Row(Modifier.height(IntrinsicSize.Max)) {
                        Segment(
                            count = ui.drivingOnMap,
                            label = "w ruchu",
                            color = VehicleState.DRIVING.color,
                            tint = androidx.compose.ui.graphics.Color(0xFFE6F4E9),
                            textColor = androidx.compose.ui.graphics.Color(0xFF1B6B2C),
                            checked = ui.showDriving,
                            onClick = vm::toggleDriving,
                            startPadding = 14.dp, endPadding = 12.dp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Segment(
                            count = ui.parkedOnMap,
                            label = "zaparkowane",
                            color = VehicleState.PARKED.color,
                            tint = androidx.compose.ui.graphics.Color(0xFFFFF1DC),
                            textColor = androidx.compose.ui.graphics.Color(0xFF8A4B00),
                            checked = ui.showParked,
                            onClick = vm::toggleParked,
                            startPadding = 12.dp, endPadding = 14.dp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                val refreshDescription = "Odśwież dane. Ostatnia aktualizacja: " + (ui.lastRefresh?.let { Fmt.stamp(it) } ?: "brak")
                Surface(
                    onClick = { vm.refresh() },
                    enabled = !ui.loading,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(start = 8.dp).height(44.dp).semantics { contentDescription = refreshDescription },
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (ui.loading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            ui.lastRefresh?.let { Fmt.shortStamp(it) } ?: "…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            ui.followed?.let { FollowInfo(ui.nameOf(it), onStop = vm::stopFollowing) }
            HiddenInfo(ui, onShowAll = vm::showAllTypes)
            ui.error?.let { err ->
                Spacer(Modifier.size(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(err, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallFloatingActionButton(onClick = { vm.mapState.fitAll(visible) }) {
                Icon(Icons.Outlined.CenterFocusStrong, "Pokaż wszystkie pojazdy")
            }
        }

        AnimatedVisibility(
            visible = ui.selected != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ui.selected?.let { v ->
                VehiclePreviewCard(
                    v,
                    name = ui.nameOf(v),
                    info = ui.infoOf(v),
                    following = ui.followId == v.deviceId,
                    preview = ui.preview?.takeIf { it.deviceId == v.deviceId },
                    onFollow = { vm.follow(v.deviceId) },
                    onClose = { vm.select(null) },
                    onDetails = { onOpenVehicle(v.deviceId) },
                    onRoutes = { onOpenRoutes(v.deviceId) },
                )
            }
        }
    }
}

/** Informacja o trybie śledzenia – wyłączana tak jak filtry widoku. */
@Composable
private fun FollowInfo(label: String, onStop: () -> Unit) {
    Spacer(Modifier.size(6.dp))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color(0xFFE8F1FE),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF9EC2F5)),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GpsFixed, null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFF1A5DBF))
            Spacer(Modifier.width(8.dp))
            Text(
                "Śledzenie: $label",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onStop) { Text("Zakończ") }
        }
    }
}

/** Informacja, że część pojazdów jest ukryta – z szybkim przywróceniem. */
@Composable
private fun HiddenInfo(ui: FleetUiState, onShowAll: () -> Unit) {
    val hiddenTypes = buildList {
        if (!ui.showDriving) add("w ruchu (${ui.drivingOnMap})")
        if (!ui.showParked) add("zaparkowane (${ui.parkedOnMap})")
    }
    if (hiddenTypes.isEmpty()) return
    Spacer(Modifier.size(6.dp))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color(0xFFFFF4E0),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFCC80)),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFF9A5B00))
            Spacer(Modifier.width(8.dp))
            Text(
                "Ukryte na mapie: ${hiddenTypes.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onShowAll) { Text("Pokaż wszystkie") }
        }
    }
}

/**
 * Połowa kapsuły: „● 5 w ruchu”. Włączona – tło w kolorze stanu; wyłączona – białe tło,
 * przekreślone oko i przekreślony opis (widać, że te pojazdy są ukryte).
 */
@Composable
private fun Segment(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    checked: Boolean,
    onClick: () -> Unit,
    startPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val fg = if (checked) textColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxHeight()
            .background(if (checked) tint else MaterialTheme.colorScheme.surface)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { onClick() })
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
        } else {
            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(14.dp), tint = color)
        }
        Spacer(Modifier.width(7.dp))
        val decoration = if (checked) null else androidx.compose.ui.text.style.TextDecoration.LineThrough
        Text(count.toString(), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            textDecoration = decoration, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(4.dp))
        Text(label, Modifier.weight(1f, fill = false), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal,
            textDecoration = decoration, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun VehiclePreviewCard(
    v: Vehicle,
    name: String,
    info: String,
    following: Boolean,
    preview: pl.fleet.monitor.ui.PreviewInfo?,
    onFollow: () -> Unit,
    onClose: () -> Unit,
    onDetails: () -> Unit,
    onRoutes: () -> Unit,
) {
    // Tryb śledzenia: delikatna niebieska ramka i jasnoniebieskie tło kafelka.
    Card(
        Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onDetails),
        elevation = CardDefaults.cardElevation(6.dp),
        border = if (following) BorderStroke(2.dp, FOLLOW_BORDER) else null,
        colors = if (following) CardDefaults.cardColors(containerColor = FOLLOW_TINT) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (info.isNotEmpty()) {
                        Text(info, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StateBadge(v.state, text = v.state.label)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Zamknij") }
            }
            val status = (preview?.status as? pl.fleet.monitor.ui.details.Loadable.Ready)?.value
            val driving = status?.motion ?: (v.state == VehicleState.DRIVING)
            // Na postoju zegary pokazywałyby same zera – pomijamy je.
            if (driving) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcGauge(v.speed.toFloat(), SPEED_MAX, v.speed.toString(), "km/h", VehicleState.DRIVING.color, Modifier.weight(1f))
                ArcGauge(
                    v.rpm?.toFloat(), RPM_MAX, v.rpm?.let { Fmt.decimal(it / 1000.0, 1) }, "obr. ×1000",
                    androidx.compose.ui.graphics.Color(0xFF1A5DBF), Modifier.weight(1f),
                )
                ArcGauge(
                    v.fuelLiters?.toFloat(), FUEL_MAX, v.fuelLiters?.let { "${Fmt.decimal(it, 0)} l" }, "paliwo",
                    VehicleState.PARKED.color, Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(10.dp))
            StatusField(v, preview, driving)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (following) {
                    // „Śledzisz” ponownie centruje mapę; śledzenie kończy tylko „Zakończ” w komunikacie u góry.
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onFollow,
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFDCE8FB),
                            contentColor = androidx.compose.ui.graphics.Color(0xFF1A3E78),
                        ),
                    ) {
                        Icon(Icons.Outlined.GpsFixed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Śledzisz", maxLines = 1)
                    }
                } else {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onFollow, enabled = v.hasPosition, modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Outlined.GpsNotFixed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Śledź", maxLines = 1)
                    }
                }
                // Trasy na mapie – bez przechodzenia przez szczegóły
                androidx.compose.material3.OutlinedButton(
                    onClick = onRoutes, enabled = v.serial.isNotBlank(), modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Outlined.Route, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trasy", maxLines = 1)
                }
                Button(
                    onClick = onDetails, modifier = Modifier.weight(1.2f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Szczegóły", maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

private val FOLLOW_BORDER = androidx.compose.ui.graphics.Color(0xFF9EC2F5)
private val FOLLOW_TINT = androidx.compose.ui.graphics.Color(0xFFF5F9FF)

private const val SPEED_MAX = 160f
private const val RPM_MAX = 6000f
/** Pojemność zbiornika nie jest znana – łuk paliwa liczony względem 100 l. */
private const val FUEL_MAX = 100f

/** Półokrągły zegar; brak wartości (null) – szary łuk i „—”. */
@Composable
private fun ArcGauge(value: Float?, max: Float, text: String?, unit: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fraction = ((value ?: 0f) / max).coerceIn(0f, 1f)
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 92.dp, height = 52.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.Canvas(Modifier.size(width = 92.dp, height = 52.dp)) {
                val stroke = 8.dp.toPx()
                val d = size.width - stroke
                val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                val arcSize = androidx.compose.ui.geometry.Size(d, d)
                val style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(track, 180f, 180f, false, topLeft, arcSize, style = style)
                if (value != null && fraction > 0f) drawArc(color, 180f, 180f * fraction, false, topLeft, arcSize, style = style)
            }
            Text(
                text ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Pole stanu pod zegarami: „W ruchu od 2 h 41 min” / „Postój od 33 min” + czas ostatniego raportu. */
@Composable
private fun StatusField(v: Vehicle, preview: pl.fleet.monitor.ui.PreviewInfo?, driving: Boolean) {
    val status = (preview?.status as? pl.fleet.monitor.ui.details.Loadable.Ready)?.value
    val stateColor = if (driving) VehicleState.DRIVING.color else VehicleState.PARKED.color
    val bg = if (driving) androidx.compose.ui.graphics.Color(0xFFF1F8F2) else androidx.compose.ui.graphics.Color(0xFFFFF6EA)
    // Początek jazdy/postoju: z funkcji status albo wyliczony z tras; bez niego nie pokazujemy czasu trwania.
    val begin = status?.begin
    val title = (if (driving) "W ruchu" else "Postój") +
        (begin?.let { " od " + Fmt.duration((pl.fleet.monitor.data.now() - it).inWholeSeconds) } ?: "")
    val report = when (val r = preview?.lastReport) {
        is pl.fleet.monitor.ui.details.Loadable.Ready -> r.value
        else -> null
    } ?: v.lastPositionTime
    val since = begin?.let { "od ${Fmt.dateTime(it)} · " }.orEmpty()
    Column(
        Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (driving) Icons.Outlined.Route else Icons.Outlined.LocalParking, null, Modifier.size(18.dp), tint = stateColor)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = stateColor)
            if (preview?.status is pl.fleet.monitor.ui.details.Loadable.Loading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        }
        Text(
            "${since}ostatni raport: ${Fmt.smartTime(report)} (${Fmt.ago(report)})",
            style = MaterialTheme.typography.bodySmall,
            color = if (Fmt.isStale(report)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
