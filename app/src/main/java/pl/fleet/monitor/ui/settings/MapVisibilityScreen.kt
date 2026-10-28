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
import pl.fleet.monitor.ui.FleetViewModel
import pl.fleet.monitor.ui.components.GradientTopBar

/** Ustawienia: które pojazdy mają być pokazywane na mapie. */
@Composable
fun MapVisibilityScreen(vm: FleetViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val visibleCount = ui.vehicles.count { it.deviceId !in ui.hidden }
    val shown = ui.vehicles.filter { v ->
        query.isBlank() || listOf(v.displayName, v.plateNo, v.serial).any { it.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Pojazdy na mapie",
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
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.setOnMap(v.deviceId, !visible) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
}
