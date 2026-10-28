package pl.fleet.monitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.FleetViewModel
import pl.fleet.monitor.ui.VehicleIcon
import pl.fleet.monitor.ui.color
import pl.fleet.monitor.ui.components.GradientTopBar

/**
 * Ustawienia pojazdów: widoczność w aplikacji (lista i mapa) oraz ikona na mapie.
 * Wyłączony pojazd znika zarówno z listy, jak i z mapy.
 */
@Composable
fun MapVisibilityScreen(vm: FleetViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<Vehicle?>(null) }
    val visibleCount = ui.vehicles.count { it.deviceId !in ui.hidden }
    val shown = ui.vehicles.filter { v ->
        query.isBlank() || listOf(v.displayName, v.plateNo, v.serial).any { it.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Pojazdy w aplikacji",
                subtitle = "Widoczne: $visibleCount z ${ui.vehicles.size}",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Szukaj pojazdu") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, "Wyczyść") }
                },
                singleLine = true,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setAllOnMap(true) }, modifier = Modifier.weight(1f)) { Text("Zaznacz wszystkie") }
                OutlinedButton(onClick = { vm.setAllOnMap(false) }, modifier = Modifier.weight(1f)) { Text("Odznacz wszystkie") }
            }
            HorizontalDivider()
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.deviceId }) { v ->
                    val visible = v.deviceId !in ui.hidden
                    val icon = ui.icons[v.deviceId] ?: VehicleIcon.DEFAULT
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.setOnMap(v.deviceId, !visible) }
                            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Ikona pojazdu – dotknięcie otwiera wybór ikony
                        IconButton(onClick = { pickFor = v }) {
                            Icon(icon.vector, "Zmień ikonę: ${icon.label}", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                v.displayName.ifBlank { v.label }, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (v.serial.isNotBlank()) {
                                Text("Nr nadajnika: ${v.serial}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = visible, onCheckedChange = { vm.setOnMap(v.deviceId, it) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    pickFor?.let { v ->
        IconPickerDialog(
            title = v.displayName.ifBlank { v.label },
            current = ui.icons[v.deviceId] ?: VehicleIcon.DEFAULT,
            onPick = { vm.setIcon(v.deviceId, it); pickFor = null },
            onDismiss = { pickFor = null },
        )
    }
}

@Composable
private fun IconPickerDialog(title: String, current: VehicleIcon, onPick: (VehicleIcon) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Ikona na mapie")
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleIcon.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { icon ->
                            val selected = icon == current
                            Surface(
                                onClick = { onPick(icon) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(icon.vector, null, Modifier.size(30.dp), tint = VehicleState.PARKED.color)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        icon.label + if (icon == VehicleIcon.DEFAULT) " (domyślna)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}
