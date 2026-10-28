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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
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
        if (ui.vehicles.isNotEmpty() && !vm.mapState.hasFitted) vm.mapState.fitAll(visible.ifEmpty { ui.vehicles })
    }

    Box(modifier.fillMaxSize()) {
        FleetMap(
            vehicles = visible,
            modifier = Modifier.fillMaxSize(),
            state = vm.mapState,
            selectedId = ui.selectedId,
            icons = ui.icons,
            labels = ui.customLabels,
            onSelect = vm::select,
        )

        // Blok „Twoje pojazdy”: filtry z licznikami + ostatnia aktualizacja z odświeżaniem
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp,
                modifier = Modifier.width(IntrinsicSize.Max),
            ) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 2.dp)) {
                    Text(
                        "Twoje pojazdy",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    TypeToggle(
                        icon = Icons.Filled.LocalShipping,
                        label = "Pojazdy w ruchu: ${ui.drivingOnMap}",
                        color = VehicleState.DRIVING.color,
                        checked = ui.showDriving,
                        onClick = vm::toggleDriving,
                    )
                    Spacer(Modifier.size(6.dp))
                    TypeToggle(
                        icon = Icons.Filled.LocalParking,
                        label = "Pojazdy zaparkowane: ${ui.parkedOnMap}",
                        color = VehicleState.PARKED.color,
                        checked = ui.showParked,
                        onClick = vm::toggleParked,
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                "Ostatnia aktualizacja",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                ui.lastRefresh?.let { Fmt.stamp(it) } ?: "…",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (ui.loading) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(Icons.Outlined.Refresh, "Odśwież dane", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
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
                VehiclePreviewCard(v, onClose = { vm.select(null) }, onDetails = { onOpenVehicle(v.deviceId) })
            }
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

/** Przełącznik typu pojazdów: ikona + liczba; wyłączony = wyszarzony. */
@Composable
private fun TypeToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (checked) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (checked) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface,
        border = if (checked) null else BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (checked) "pokazane na mapie" else "ukryte na mapie" },
    ) {
        Row(
            Modifier
                .then(if (checked) Modifier.background(pl.fleet.monitor.ui.theme.stateGradient(color)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Wyłączony filtr: przekreślone oko i przekreślony opis – widać, że te auta są ukryte.
            Icon(if (checked) icon else Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp), tint = if (checked) fg else color)
            Spacer(Modifier.width(8.dp))
            Text(
                label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                textDecoration = if (checked) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
            )
        }
    }
}

@Composable
private fun VehiclePreviewCard(v: Vehicle, onClose: () -> Unit, onDetails: () -> Unit) {
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
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
                Text("Szczegóły i historia")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}
