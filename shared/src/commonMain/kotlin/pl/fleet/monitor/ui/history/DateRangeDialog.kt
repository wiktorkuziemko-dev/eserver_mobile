package pl.fleet.monitor.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import pl.fleet.monitor.ui.Fmt
import kotlin.time.Instant

/** Wybór zakresu dat (do dziś włącznie) o długości najwyżej [maxDays] dni. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    from: LocalDate,
    to: LocalDate,
    maxDays: Int,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from.toEpochMillis(),
        initialSelectedEndDateMillis = to.toEpochMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= Fmt.today().toEpochMillis()
        },
    )
    val startSel = state.selectedStartDateMillis?.toLocalDate()
    val endSel = state.selectedEndDateMillis?.toLocalDate() ?: startSel
    val days = if (startSel != null && endSel != null) startSel.daysUntil(endSel) + 1 else 0
    val tooLong = days > maxDays
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = startSel != null && !tooLong,
                onClick = { if (startSel != null && endSel != null) onPick(startSel, endSel) },
            ) { Text("Pokaż") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f), title = {
            Column(Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)) {
                Text("Wybierz zakres dat (maks. $maxDays dni)")
                if (tooLong) {
                    Text(
                        "Wybrany okres ma $days dni – skróć go do $maxDays dni.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        })
    }
}

private fun LocalDate.toEpochMillis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
