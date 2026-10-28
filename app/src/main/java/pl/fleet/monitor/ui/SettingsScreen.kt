package pl.fleet.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.BuildConfig
import pl.fleet.monitor.ui.components.SectionTitle

@Composable
fun SettingsScreen(vm: FleetViewModel, modifier: Modifier = Modifier) {
    val creds by vm.session.credentials.collectAsStateWithLifecycle()
    var interval by remember { mutableIntStateOf(vm.session.refreshIntervalSec) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionTitle("Konto")
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(creds?.login.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(vm.session.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle("Automatyczne odświeżanie pozycji")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 120).forEach { sec ->
                FilterChip(
                    selected = interval == sec,
                    onClick = { interval = sec; vm.session.refreshIntervalSec = sec },
                    label = { Text(if (sec < 60) "$sec s" else "${sec / 60} min") },
                )
            }
        }

        SectionTitle("Legenda")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pl.fleet.monitor.data.VehicleState.entries.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pl.fleet.monitor.ui.components.StateBadge(s)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when (s) {
                            pl.fleet.monitor.data.VehicleState.DRIVING -> "zapłon włączony, prędkość > 0"
                            pl.fleet.monitor.data.VehicleState.IDLE -> "zapłon włączony, pojazd stoi"
                            pl.fleet.monitor.data.VehicleState.PARKED -> "zapłon wyłączony"
                            pl.fleet.monitor.data.VehicleState.UNKNOWN -> "brak pozycji lub stanu zapłonu"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.size(32.dp))
        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null)
            Spacer(Modifier.size(8.dp))
            Text("Wyloguj")
        }
        Spacer(Modifier.size(16.dp))
        Text(
            "Wersja ${BuildConfig.VERSION_NAME} · Mapa © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Wylogować?") },
            text = { Text(if (vm.session.rememberEnabled) "Login i hasło pozostaną zapamiętane na ekranie logowania. Aby je usunąć, odznacz „Zapamiętaj dane logowania” przy następnym logowaniu." else "Sesja zostanie zakończona.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Wyloguj") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Anuluj") } },
        )
    }
}
