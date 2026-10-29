package pl.fleet.monitor.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Logo E-SERVER mini: czerwona pinezka z białym pierścieniem i grafitowa plakietka „mini”.
 * Te same kształty co ikona aplikacji (układ 108×108, przycięty do części widocznej).
 */
val AppLogo: ImageVector by lazy {
    ImageVector.Builder("AppLogo", 72.dp, 72.dp, viewportWidth = 72f, viewportHeight = 72f)
        .apply {
            // Przesunięcie o -18: z układu ikony 108×108 do widocznego obszaru 72×72.
            fun p(d: String) = addPathNodes(d)
            addPath(p("M23,61a10,2.4 0 1,0 20,0a10,2.4 0 1,0 -20,0"), fill = SolidColor(Color(0x1F000000)))
            addPath(
                p("M33,7c-10.5,0 -19,8.5 -19,19c0,14.2 19,34 19,34s19,-19.8 19,-34c0,-10.5 -8.5,-19 -19,-19z"),
                fill = Brush.linearGradient(listOf(Color(0xFFF0584E), Color(0xFFB71C1C)), Offset(14f, 7f), Offset(52f, 60f)),
            )
            addPath(
                p("M27,26a6,6 0 1,0 12,0a6,6 0 1,0 -12,0"),
                stroke = SolidColor(Color.White), strokeLineWidth = 3f,
            )
            addPath(
                p("M46.25,46h13.5a5.25,5.25 0 0,1 0,10.5h-13.5a5.25,5.25 0 0,1 0,-10.5z"),
                fill = SolidColor(Color(0xFF26262A)), stroke = SolidColor(Color.White), strokeLineWidth = 1.2f,
            )
            addPath(
                p(
                    "M46.45,53.8V49.9M46.45,50.8C46.45,49.3 49.15,49.3 49.15,50.8V53.8" +
                        "M49.15,50.8C49.15,49.3 51.85,49.3 51.85,50.8V53.8" +
                        "M53.45,49.9V53.8M53.45,48.3V48.35" +
                        "M55.25,53.8V49.9M55.25,50.8C55.25,49.3 57.95,49.3 57.95,50.8V53.8" +
                        "M59.55,49.9V53.8M59.55,48.3V48.35",
                ),
                stroke = SolidColor(Color.White), strokeLineWidth = 1.15f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        .build()
}

/** Wielokolorowa pinezka w stylu Google Maps (przycisk „Pokaż w Google Maps”). */
val GoogleMapsPin: ImageVector by lazy {
    ImageVector.Builder("GoogleMapsPin", 24.dp, 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            addPath(addPathNodes("M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7z"), fill = SolidColor(Color(0xFFEA4335)))
            addPath(addPathNodes("M5,9c0,2 1,4.3 2.3,6.4L12,9z"), fill = SolidColor(Color(0xFF4285F4)))
            addPath(addPathNodes("M19,9c0,2 -1,4.3 -2.3,6.4L12,9z"), fill = SolidColor(Color(0xFFFBBC04)))
            addPath(addPathNodes("M7.3,15.4C9.4,18.8 12,22 12,22s2.6,-3.2 4.7,-6.6L12,9z"), fill = SolidColor(Color(0xFF34A853)))
            addPath(addPathNodes("M12,9m-2.6,0a2.6,2.6 0,1 1,5.2 0a2.6,2.6 0,1 1,-5.2 0"), fill = SolidColor(Color.White))
        }
        .build()
}
