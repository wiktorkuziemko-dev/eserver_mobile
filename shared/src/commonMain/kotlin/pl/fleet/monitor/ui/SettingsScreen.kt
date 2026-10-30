package pl.fleet.monitor.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import pl.fleet.monitor.data.SessionStore
import pl.fleet.monitor.ui.map.MarkerPreview
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.ui.components.SectionTitle

@Composable
fun SettingsScreen(vm: FleetViewModel, onOpenVisibility: () -> Unit, modifier: Modifier = Modifier) {
    val fleet by vm.ui.collectAsStateWithLifecycle()
    val creds by vm.session.credentials.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionTitle("Konto")
        Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().background(pl.fleet.monitor.ui.theme.BrandGradient).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(44.dp), tint = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(creds?.login.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White)
                    Text("Zalogowany użytkownik", style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                }
            }
        }

        SectionTitle("Pojazdy")
        Card(onClick = onOpenVisibility, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Widoczność, ikony i opisy pojazdów", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val visible = fleet.vehicles.count { it.deviceId !in fleet.hidden }
                    Text("Widoczne w aplikacji: $visible z ${fleet.vehicles.size}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("Mapa")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Workspaces, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Grupuj pojazdy blisko siebie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Nakładające się ikony łączą się w kółko z liczbą pojazdów. Zaznaczony pojazd jest zawsze widoczny osobno.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = fleet.clusterMarkers, onCheckedChange = vm::setClusterMarkers)
                }

                HorizontalDivider(Modifier.padding(vertical = 14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nie wygaszaj ekranu na mapie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Ekran pozostaje włączony, dopóki widoczna jest mapa. Zwiększa zużycie baterii.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = fleet.keepScreenOn, onCheckedChange = vm::setKeepScreenOn)
                }

                HorizontalDivider(Modifier.padding(vertical = 14.dp))

                // Podgląd zmienia się na bieżąco, zapis po puszczeniu suwaka.
                var scale by remember(fleet.markerScale) { mutableFloatStateOf(fleet.markerScale) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FormatSize, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text("Wielkość ikon i opisów", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = { vm.setMarkerScale(scale) },
                    valueRange = SessionStore.MIN_MARKER_SCALE..SessionStore.MAX_MARKER_SCALE,
                    steps = 7,
                )
                Box(
                    Modifier.fillMaxWidth().height(110.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2EFE9)),
                    contentAlignment = Alignment.Center,
                ) {
                    MarkerPreview(scale)
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
            "Wersja ${vm.appVersion} · Mapa © OpenStreetMap contributors",
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
