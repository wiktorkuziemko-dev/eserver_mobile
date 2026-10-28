package pl.fleet.monitor.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import pl.fleet.monitor.ui.color
import pl.fleet.monitor.ui.theme.stateGradient
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.FleetUiState
import pl.fleet.monitor.ui.FleetViewModel
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.components.ErrorState
import pl.fleet.monitor.ui.components.StateBadge
import pl.fleet.monitor.ui.shortLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(vm: FleetViewModel, ui: FleetUiState, onOpenVehicle: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            placeholder = { Text("Szukaj: nazwa, nr rejestracyjny, nr nadajnika") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Clear, "Wyczyść") }
            },
            singleLine = true,
        )
        Spacer(Modifier.size(8.dp))
        if (ui.loading && ui.vehicles.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            ui.vehicles.isEmpty() && ui.error != null && !ui.loading -> ErrorState(ui.error, onRetry = vm::refresh)
            else -> PullToRefreshBox(isRefreshing = ui.loading && ui.vehicles.isNotEmpty(), onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (ui.error != null) {
                        item {
                            Text(ui.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (ui.filtered.isEmpty() && !ui.loading) {
                        item {
                            Text(
                                "Brak pojazdów spełniających kryteria.",
                                Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(ui.filtered, key = { it.deviceId }) { v ->
                        VehicleRow(v, onClick = { onOpenVehicle(v.deviceId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleRow(v: Vehicle, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
      Row(Modifier.height(IntrinsicSize.Min)) {
        // Pasek koloru stanu z gradientem
        Box(Modifier.width(5.dp).fillMaxHeight().background(stateGradient(v.state.color)))
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Nazwa pojazdu jak na mapie, pod nią numer nadajnika
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        v.displayName.ifBlank { v.label },
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    StateBadge(v.state)
                }
                if (v.serial.isNotBlank()) {
                    Text(
                        "Nr nadajnika: ${v.serial}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Meta(Icons.Outlined.Speed, Fmt.speed(v.speed))
                    if (v.state == VehicleState.DRIVING) Meta(Icons.Outlined.Explore, Fmt.heading(v.heading).substringBefore(" "))
                    Meta(
                        Icons.Outlined.Schedule, Fmt.ago(v.lastPositionTime),
                        warn = Fmt.isStale(v.lastPositionTime),
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String, warn: Boolean = false) {
    val color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
