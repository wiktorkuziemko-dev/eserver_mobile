package pl.fleet.monitor.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import pl.fleet.monitor.R
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.color
import pl.fleet.monitor.ui.components.InfoTile
import pl.fleet.monitor.ui.components.SectionTitle
import pl.fleet.monitor.ui.components.StateBadge
import pl.fleet.monitor.ui.label
import pl.fleet.monitor.ui.map.FleetMap
import pl.fleet.monitor.ui.map.FleetMapState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailsScreen(
    vehicle: Vehicle,
    vm: VehicleDetailsViewModel,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mapState = remember(vehicle.deviceId) { FleetMapState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vehicle.label, fontWeight = FontWeight.Bold)
                        if (vehicle.name.isNotBlank()) Text(vehicle.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
                actions = {
                    IconButton(onClick = { onRefresh(); vm.load() }) { Icon(Icons.Outlined.Refresh, "Odśwież") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (vehicle.hasPosition) {
                FleetMap(
                    vehicles = listOf(vehicle),
                    state = mapState,
                    selectedId = vehicle.deviceId,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                )
                androidx.compose.runtime.LaunchedEffect(vehicle.latitude, vehicle.longitude) {
                    mapState.fitAll(listOf(vehicle))
                }
            }

            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateBadge(vehicle.state, text = vehicle.state.label)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Pozycja: ${Fmt.ago(vehicle.lastPositionTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (Fmt.isStale(vehicle.lastPositionTime)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Bieżąca aktywność")
            CurrentActivityCard(ui.status, vehicle)

            SectionTitle("Dane pojazdu")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(Icons.Outlined.Speed, "Prędkość", Fmt.speed(vehicle.speed), Modifier.weight(1f))
                InfoTile(Icons.Outlined.Key, "Zapłon", Fmt.ignition(vehicle.ignition), Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(Icons.Outlined.Explore, "Kierunek", Fmt.heading(vehicle.heading), Modifier.weight(1f))
                InfoTile(Icons.Outlined.Route, "Przebieg", Fmt.mileage(vehicle.mileage), Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
            DetailsList(
                listOf(
                    Triple(Icons.Outlined.Schedule, "Czas ostatniej pozycji", Fmt.dateTime(vehicle.lastPositionTime)),
                    Triple(Icons.Outlined.CellTower, "Ostatni raport nadajnika", when (val r = ui.lastReport) {
                        Loadable.Loading -> "…"
                        is Loadable.Failed -> "niedostępne"
                        is Loadable.Ready -> r.value?.let { "${Fmt.dateTime(it)} (${Fmt.ago(it)})" } ?: "—"
                    }),
                    Triple(Icons.Outlined.MyLocation, "Współrzędne", Fmt.coords(vehicle.latitude, vehicle.longitude)),
                    Triple(Icons.Outlined.Tag, "Nr urządzenia GPS", vehicle.serial.ifBlank { "—" }),
                ),
                onClickValue = { label, value -> copy(context, label, value) },
            )

            SectionTitle("Dzisiaj")
            when (val t = ui.today) {
                Loadable.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                is Loadable.Failed -> Text(t.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is Loadable.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile(Icons.Outlined.Route, "Dystans", Fmt.km(t.value.distanceKm), Modifier.weight(1f))
                    InfoTile(Icons.Outlined.Timer, "Jazda", Fmt.duration(t.value.drivingSeconds), Modifier.weight(1f))
                    InfoTile(Icons.Outlined.LocalParking, "Postój", Fmt.duration(t.value.parkingSeconds), Modifier.weight(1f))
                }
            }

            Spacer(Modifier.size(20.dp))
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.History, null); Spacer(Modifier.width(8.dp)); Text("Historia tras")
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = { openInGoogleMaps(context, vehicle) },
                modifier = Modifier.fillMaxWidth(),
                enabled = vehicle.hasPosition,
            ) {
                Icon(painterResource(R.drawable.ic_google_maps), null, Modifier.size(20.dp), tint = Color.Unspecified)
                Spacer(Modifier.width(8.dp))
                Text("Pokaż w Google Maps")
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = vm::runDiagnostics, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.BugReport, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Diagnostyka API")
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    ui.diagnostics?.let { d -> DiagnosticsDialog(d, vehicle.label, onClose = vm::closeDiagnostics) }
}

@Composable
private fun DiagnosticsDialog(d: Loadable<String>, label: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = when (d) {
        Loadable.Loading -> null
        is Loadable.Failed -> d.message
        is Loadable.Ready -> d.value
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnostyka API") },
        text = {
            if (text == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp)); Text("Wysyłanie zapytań testowych…")
                }
            } else {
                SelectionContainer {
                    Text(
                        text,
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text != null, onClick = {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Diagnostyka API – $label")
                    .putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(intent, "Wyślij diagnostykę"))
            }) { Text("Udostępnij") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Zamknij") } },
    )
}

@Composable
private fun CurrentActivityCard(status: Loadable<pl.fleet.monitor.data.MotionStatus>, vehicle: Vehicle) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (status) {
                Loadable.Loading -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp)); Text("Pobieranie stanu…")
                }
                is Loadable.Failed -> {
                    // Funkcja status nie zwróciła danych – pokazujemy stan wynikający z ostatniej pozycji.
                    val driving = vehicle.state == pl.fleet.monitor.data.VehicleState.DRIVING
                    val stateColor = vehicle.state.color
                    Icon(if (driving) Icons.Outlined.Route else Icons.Outlined.LocalParking, null, Modifier.size(32.dp), tint = stateColor)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(vehicle.state.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = stateColor)
                        Text("według ostatniej pozycji (${Fmt.ago(vehicle.lastPositionTime)})", style = MaterialTheme.typography.bodySmall)
                        Text(status.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is Loadable.Ready -> {
                    val s = status.value
                    val stateColor = if (s.motion) pl.fleet.monitor.data.VehicleState.DRIVING.color else pl.fleet.monitor.data.VehicleState.PARKED.color
                    Icon(
                        if (s.motion) Icons.Outlined.Route else Icons.Outlined.LocalParking, null,
                        Modifier.size(32.dp), tint = stateColor,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (s.motion) "Jazda" else "Postój",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = stateColor,
                        )
                        if (s.begin != null) {
                            val since = java.time.Duration.between(s.begin, java.time.ZonedDateTime.now()).seconds
                            Text("od ${Fmt.smartTime(s.begin)} · trwa ${Fmt.duration(since)}", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("brak informacji o początku aktywności", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsList(rows: List<Triple<ImageVector, String, String>>, onClickValue: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        rows.forEachIndexed { i, (icon, label, value) ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onClickValue(label, value) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun copy(context: Context, label: String, value: String) {
    if (value == "—") return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Skopiowano: $label", Toast.LENGTH_SHORT).show()
}

/** Otwiera pozycję pojazdu w aplikacji Google Maps (lub w przeglądarce, gdy aplikacji brak). */
private fun openInGoogleMaps(context: Context, v: Vehicle) {
    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${v.latitude},${v.longitude}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps")) }
        .recoverCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Nie można otworzyć Google Maps", Toast.LENGTH_SHORT).show() }
}
