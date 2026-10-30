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
fun MapScreen(vm: FleetViewModel, ui: FleetUiState, onOpenVehicle: (String) -> Unit, modifier: Modifier = Modifier) {
    val visible = ui.mapVehicles

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
            ui.followed?.let { FollowInfo(ui.mapLabel(it), onStop = vm::stopFollowing) }
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
            SmallFloatingActionButton(onClick = { vm.stopFollowing(); vm.mapState.fitAll(visible) }) {
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
                    following = ui.followId == v.deviceId,
                    onFollow = { if (ui.followId == v.deviceId) vm.stopFollowing() else vm.follow(v.deviceId) },
                    onClose = { vm.select(null) },
                    onDetails = { onOpenVehicle(v.deviceId) },
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
    following: Boolean,
    onFollow: () -> Unit,
    onClose: () -> Unit,
    onDetails: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onDetails),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (v.displayName != v.label) {
                        Text(v.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Zamknij") }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StateBadge(v.state, text = v.state.label)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Fmt.speed(v.speed), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Fmt.ago(v.lastPositionTime), style = MaterialTheme.typography.bodyMedium,
                        color = if (Fmt.isStale(v.lastPositionTime)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (following) {
                    androidx.compose.material3.FilledTonalButton(onClick = onFollow, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.GpsFixed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Śledzisz", maxLines = 1)
                    }
                } else {
                    androidx.compose.material3.OutlinedButton(onClick = onFollow, enabled = v.hasPosition, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.GpsNotFixed, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Śledź", maxLines = 1)
                    }
                }
                Button(onClick = onDetails, modifier = Modifier.weight(1.4f)) {
                    Text("Szczegóły", maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
