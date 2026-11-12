package pl.fleet.monitor.ui.components

import androidx.compose.foundation.Canvas
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

/**
 * Siła sygnału jak w telefonie: 4 kreski rosnącej wysokości, wypełnione według poziomu 0–31
 * (przeliczonego na 0–4 kreski), z podpisem „GSM” / „GPS”.
 */
@Composable
fun SignalIndicator(label: String, level: Int?, modifier: Modifier = Modifier) {
    val bars = signalBars(level)
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.semantics { contentDescription = "$label: ${level?.let { "$bars z 4" } ?: "brak danych"}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 13.dp)) {
            val w = size.width / 4
            val gap = 1.5.dp.toPx()
            for (i in 0 until 4) {
                val bh = size.height * (i + 1) / 4
                drawRoundRect(
                    if (level != null && i < bars) on else off,
                    topLeft = Offset(i * w + gap / 2, size.height - bh),
                    size = Size(w - gap, bh),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
        Text(label, fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Napięcie akumulatora z ikoną akumulatora samochodowego. [ok] = null – bez oceny (szary opis),
 * true – zielona plakietka, false – czerwona plakietka z wykrzyknikiem.
 */
@Composable
fun BatteryIndicator(volts: Double?, ok: Boolean?, modifier: Modifier = Modifier) {
    val text = volts?.let { "${Fmt.decimal(it, 1)} V" } ?: "— V"
    if (ok == null || volts == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(CarBatteryIcon, "Akumulator", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val fg = if (ok) OK_FG else LOW_FG
        Row(
            modifier.background(if (ok) OK_BG else LOW_BG, RoundedCornerShape(50)).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CarBatteryIcon, if (ok) "Akumulator" else "Niski stan akumulatora", Modifier.size(15.dp), tint = fg)
            Spacer(Modifier.width(3.dp))
            Text(if (ok) text else "$text !", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

/** Zestaw wskaźników stanu nadajnika: GSM, GPS i akumulator – na kafelku mapy i na liście pojazdów. */
@Composable
fun TelemetryIndicators(telemetry: VehicleTelemetry?, batteryOk: Boolean?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SignalIndicator("GSM", telemetry?.gsm)
        SignalIndicator("GPS", telemetry?.gps)
        BatteryIndicator(telemetry?.volts, batteryOk)
    }
}
