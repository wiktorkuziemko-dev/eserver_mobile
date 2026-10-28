package pl.fleet.monitor.ui.history

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.fleet.monitor.data.HistoryEntry
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.Fmt
import pl.fleet.monitor.ui.color
import pl.fleet.monitor.ui.components.ErrorState
import pl.fleet.monitor.ui.components.InfoTile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val ColType = 28.dp
private val ColTime = 64.dp
private val ColKm = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vehicleLabel: String, vm: HistoryViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historia tras", fontWeight = FontWeight.Bold)
                        Text(vehicleLabel, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
                actions = {
                    IconButton(
                        enabled = ui.summary?.entries?.isNotEmpty() == true,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Historia tras $vehicleLabel")
                                .putExtra(Intent.EXTRA_TEXT, vm.csv(vehicleLabel))
                            context.startActivity(Intent.createChooser(intent, "Eksportuj historię (CSV)"))
                        },
                    ) { Icon(Icons.Outlined.Share, "Eksportuj CSV") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RangePreset.entries.filter { it != RangePreset.CUSTOM }.forEach { p ->
                    FilterChip(selected = ui.preset == p, onClick = { vm.selectPreset(p) }, label = { Text(p.label) })
                }
                FilterChip(
                    selected = ui.preset == RangePreset.CUSTOM,
                    onClick = { showPicker = true },
                    label = { Text(RangePreset.CUSTOM.label) },
                    leadingIcon = { Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp)) },
                )
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ui.from == ui.to) Fmt.dayHeader(ui.from) else "${Fmt.date(ui.from)} – ${Fmt.date(ui.to)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("Tylko jazdy", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = ui.onlyTrips, onCheckedChange = { vm.toggleOnlyTrips() })
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.size(4.dp))

            val error = ui.error
            when {
                error != null -> ErrorState(error, onRetry = vm::load)
                ui.summary != null -> HistoryContent(ui)
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = ui.from.toEpochMillis(),
            initialSelectedEndDateMillis = ui.to.toEpochMillis(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= LocalDate.now().toEpochMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!.toLocalDate()
                        val end = state.selectedEndDateMillis?.toLocalDate() ?: start
                        showPicker = false
                        vm.setCustomRange(start, end)
                    },
                ) { Text("Pokaż") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Anuluj") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f), title = {
                Text("Wybierz zakres dat", Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

@Composable
private fun HistoryContent(ui: HistoryUiState) {
    val summary = ui.summary ?: return
    val days = ui.days
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile(Icons.Outlined.Route, "Dystans", Fmt.km(summary.distanceKm), Modifier.weight(1f))
                    InfoTile(Icons.Outlined.Timer, "Czas jazdy", Fmt.duration(summary.drivingSeconds), Modifier.weight(1f))
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile(Icons.Outlined.LocalParking, "Czas postoju", Fmt.duration(summary.parkingSeconds), Modifier.weight(1f))
                    InfoTile(Icons.Outlined.Route, "Liczba jazd", ui.tripsCount.toString(), Modifier.weight(1f))
                }
            }
        }
        if (days.isEmpty()) {
            item {
                Text(
                    "Brak aktywności w wybranym okresie.",
                    Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        days.forEach { day ->
            item(key = "h-${day.date}") { DayHeader(day) }
            item(key = "c-${day.date}") { ColumnsHeader() }
            itemsIndexed(day.entries, key = { i, _ -> "${day.date}-$i" }) { _, e -> EntryRow(e, day.date) }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Fmt.dayHeader(day.date), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            "${day.trips} jazd · ${Fmt.km(day.distanceKm)} · ${Fmt.duration(day.drivingSeconds)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ColumnsHeader() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Spacer(Modifier.width(ColType))
        Text("Od", Modifier.width(ColTime), style = style, color = color)
        Text("Do", Modifier.width(ColTime), style = style, color = color)
        Text("Czas", Modifier.weight(1f), style = style, color = color)
        Text("Dystans", Modifier.width(ColKm), style = style, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun EntryRow(e: HistoryEntry, day: LocalDate) {
    val tint = if (e.motion) VehicleState.DRIVING.color else VehicleState.PARKED.color
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(ColType)) {
                Icon(if (e.motion) Icons.Outlined.Route else Icons.Outlined.LocalParking, if (e.motion) "Jazda" else "Postój",
                    Modifier.size(18.dp), tint = tint)
            }
            TimeCell(e.begin, day)
            TimeCell(e.end, day)
            Text(Fmt.duration(e.durationSeconds), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (e.motion) Fmt.km(e.distanceKm) else "",
                Modifier.width(ColKm),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** Godzina; jeśli zdarzenie wypada innego dnia niż grupa (np. postój przez noc) – dodatkowo data. */
@Composable
private fun TimeCell(t: java.time.ZonedDateTime?, day: LocalDate) {
    Column(Modifier.width(ColTime)) {
        Text(Fmt.time(t), style = MaterialTheme.typography.bodyMedium)
        if (t != null && Fmt.local(t).toLocalDate() != day) {
            Text(Fmt.local(t).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LocalDate.toEpochMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
