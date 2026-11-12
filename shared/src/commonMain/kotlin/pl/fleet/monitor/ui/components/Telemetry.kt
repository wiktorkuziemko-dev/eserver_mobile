package pl.fleet.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.fleet.monitor.data.VehicleTelemetry
import pl.fleet.monitor.data.signalBars
import pl.fleet.monitor.ui.Fmt

/** Akumulator samochodowy: obudowa, dwa bieguny, znaki „−” i „+” (wycięte). */
val CarBatteryIcon: ImageVector by lazy {
    ImageVector.Builder("CarBattery", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(
                // bieguny
                "M5,5h4v2H5z M15,5h4v2h-4z" +
                    // obudowa
                    " M3,7h18a1,1 0 0 1 1,1v11a1,1 0 0 1 -1,1H3a1,1 0 0 1 -1,-1V8a1,1 0 0 1 1,-1z" +
                    // „−” po lewej
                    " M5,12.25h4v1.5H5z" +
                    // „+” po prawej
                    " M15,12.25h1.25v-1.25h1.5v1.25H19v1.5h-1.25v1.25h-1.5v-1.25H15z",
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
        .build()
}

private val OK_BG = Color(0xFFE6F4E9)
private val OK_FG = Color(0xFF1B6B2C)
private val LOW_BG = Color(0xFFFDE2E1)
private val LOW_FG = Color(0xFFB3261E)

private val CHIP_BG = Color(0xFFF1F2F4)
private val CHIP_SHAPE = RoundedCornerShape(8.dp)

/** Pojedynczy chip: ikona + krótki tekst, szare tło (akumulator – kolorowe przy ocenie progu). */
@Composable
private fun Chip(bg: Color, content: @Composable () -> Unit) {
    Row(
        Modifier.background(bg, CHIP_SHAPE).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) { content() }
}

/** Kreski siły sygnału (0–4) jak w telefonie. */
@Composable
private fun SignalBars(level: Int?) {
    val bars = signalBars(level)
    val on = MaterialTheme.colorScheme.onSurface
    val off = Color(0xFFC9CDD2)
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val w = size.width / 4
        val gap = 1.3.dp.toPx()
        for (i in 0 until 4) {
            val bh = size.height * (i + 1) / 4
            drawRoundRect(
                if (i < bars) on else off,
                topLeft = Offset(i * w + gap / 2, size.height - bh),
                size = Size(w - gap, bh),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/**
 * Rząd chipów stanu nadajnika – identyczny na liście pojazdów i na kafelku mapy:
 * GSM i GPS (kreski 0–4), akumulator (zielony / czerwony z „!” przy włączonym ostrzeganiu)
 * i – gdy [fuel] podane – paliwo. Bez żadnych danych z nadajnika: jeden chip „Brak danych…”.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TelemetryChips(telemetry: VehicleTelemetry?, batteryOk: Boolean?, fuel: Double?, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val hasData = telemetry != null && (telemetry.gsm != null || telemetry.gps != null || telemetry.volts != null)
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!hasData) {
            Chip(CHIP_BG) {
                Text("Brak danych z nadajnika (GSM, GPS, akumulator)", style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            telemetry!!.gsm?.let { Chip(CHIP_BG) { SignalBars(it); Text("GSM", style = labelStyle) } }
            telemetry.gps?.let { Chip(CHIP_BG) { SignalBars(it); Text("GPS", style = labelStyle) } }
            telemetry.volts?.let { v ->
                val text = "${Fmt.decimal(v, 1)} V"
                when (batteryOk) {
                    null -> Chip(CHIP_BG) { Icon(CarBatteryIcon, "Akumulator", Modifier.size(16.dp)); Text(text, style = labelStyle) }
                    true -> Chip(OK_BG) {
                        Icon(CarBatteryIcon, "Akumulator", Modifier.size(16.dp), tint = OK_FG)
                        Text(text, style = labelStyle, fontWeight = FontWeight.Bold, color = OK_FG)
                    }
                    false -> Chip(LOW_BG) {
                        Icon(CarBatteryIcon, "Niski stan akumulatora", Modifier.size(16.dp), tint = LOW_FG)
                        Text("$text !", style = labelStyle, fontWeight = FontWeight.Bold, color = LOW_FG)
                    }
                }
            }
        }
        fuel?.let { f ->
            Chip(CHIP_BG) {
                Icon(androidx.compose.material.icons.Icons.Outlined.LocalGasStation, "Paliwo", Modifier.size(15.dp))
                Text("${Fmt.decimal(f, 0)} l", style = labelStyle)
            }
        }
    }
}
