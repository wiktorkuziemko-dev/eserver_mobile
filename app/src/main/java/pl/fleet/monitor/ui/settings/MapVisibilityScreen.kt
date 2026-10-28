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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
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
                            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon.vector, icon.label, Modifier.padding(horizontal = 8.dp).size(26.dp),
                            tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                v.displayName.ifBlank { v.label }, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (v.serial.isNotBlank()) {
                                Text("Nr nadajnika: ${v.serial}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ui.customLabels[v.deviceId]?.let {
                                Text("Opis na mapie: $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        // Przełącznik z podpisem – jasne, że dotyczy widoczności
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = visible, onCheckedChange = { vm.setOnMap(v.deviceId, it) })
                            Text(
                                if (visible) "Widoczny" else "Ukryty",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pickFor = v }) {
                            Icon(Icons.Outlined.Edit, "Edytuj ikonę i opis", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    pickFor?.let { v ->
        EditVehicleDialog(
            vehicleName = v.displayName.ifBlank { v.label },
            currentIcon = ui.icons[v.deviceId] ?: VehicleIcon.DEFAULT,
            currentLabel = ui.customLabels[v.deviceId].orEmpty(),
            onSave = { icon, label -> vm.setAppearance(v.deviceId, icon, label); pickFor = null },
            onDismiss = { pickFor = null },
        )
    }
}

/** Edycja wyglądu pojazdu na mapie: własny opis i ikona. */
@Composable
private fun EditVehicleDialog(
    vehicleName: String,
    currentIcon: VehicleIcon,
    currentLabel: String,
    onSave: (VehicleIcon, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by remember { mutableStateOf(currentIcon) }
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edytuj pojazd")
                Text(vehicleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Opis na mapie") },
                    placeholder = { Text(vehicleName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingText = { Text("Puste pole = nazwa pojazdu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Ikona", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                VehicleIcon.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = option == icon
                            Surface(
                                onClick = { icon = option },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(option.vector, null, Modifier.size(30.dp), tint = VehicleState.PARKED.color)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        option.label + if (option == VehicleIcon.DEFAULT) " (domyślna)" else "",
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
        confirmButton = { TextButton(onClick = { onSave(icon, label) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
