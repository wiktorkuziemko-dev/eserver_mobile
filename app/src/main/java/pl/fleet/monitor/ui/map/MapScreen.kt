package pl.fleet.monitor.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Button
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
    val visible = ui.filtered

    LaunchedEffect(ui.vehicles.isNotEmpty()) {
        if (ui.vehicles.isNotEmpty() && !vm.mapState.hasFitted) vm.mapState.fitAll(ui.vehicles)
    }

    Box(modifier.fillMaxSize()) {
        FleetMap(
            vehicles = visible,
            modifier = Modifier.fillMaxSize(),
            state = vm.mapState,
            selectedId = ui.selectedId,
            onSelect = vm::select,
        )

        // Pasek podsumowania floty
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()) {
            Card(elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Pojazdy: ${ui.vehicles.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (ui.loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            ui.lastRefresh?.let { "Aktualizacja ${Fmt.timeSec(it)}" } ?: "Ładowanie…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(VehicleState.DRIVING, VehicleState.IDLE, VehicleState.PARKED, VehicleState.UNKNOWN).forEach { s ->
                            val n = ui.count(s)
                            if (n > 0 || s != VehicleState.UNKNOWN) {
                                FilterChip(
                                    selected = ui.stateFilter == s,
                                    onClick = { vm.setFilter(s) },
                                    label = { Text("${s.shortLabel}: $n") },
                                    leadingIcon = { StateDot(s) },
                                )
                            }
                        }
                    }
                }
            }
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
            SmallFloatingActionButton(onClick = { vm.refresh() }) { Icon(Icons.Outlined.Refresh, "Odśwież") }
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

@Composable
private fun StateDot(s: VehicleState) {
    Box(Modifier.size(10.dp).background(s.color, CircleShape))
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
                    if (v.name.isNotBlank()) {
                        Text(v.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
