package pl.fleet.monitor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Snowmobile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ikona pojazdu wybierana w ustawieniach (domyślnie auto osobowe).
 * Kolor ikony na mapie zawsze wynika ze stanu: zielony w ruchu, pomarańczowy na postoju.
 */
enum class VehicleIcon(val label: String) {
    CAR("Auto osobowe"),
    VAN("Dostawcze"),
    TRUCK("TIR"),
    GARBAGE("Śmieciarka"),
    EXCAVATOR("Koparka"),
    TRACTOR("Traktor"),
    QUAD("Quad"),
    SNOWMOBILE("Skuter śnieżny"),
    BOAT("Łódź"),
    PIN("Pinezka");

    val vector: ImageVector
        get() = when (this) {
            CAR -> Icons.Filled.DirectionsCar
            VAN -> Icons.Filled.AirportShuttle
            TRUCK -> Icons.Filled.LocalShipping
            GARBAGE -> GarbageTruck
            EXCAVATOR -> Excavator
            TRACTOR -> Icons.Filled.Agriculture
            QUAD -> Quad
            SNOWMOBILE -> Icons.Filled.Snowmobile
            BOAT -> Icons.Filled.DirectionsBoat
            PIN -> Icons.Filled.Place
        }

    companion object {
        val DEFAULT = CAR
        fun fromKey(key: String?): VehicleIcon = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

private fun customIcon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(pathData),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        .build()

/** Śmieciarka: zabudowa z przetłoczeniami, kabina z szybą, koła. */
private val GarbageTruck: ImageVector by lazy {
    customIcon(
        "GarbageTruck",
        "M1,6h13v10H1z M3.5,8h1.5v6H3.5z M6.75,8h1.5v6h-1.5z M10,8h1.5v6H10z" +
            " M15,9h4.2l2.8,3.5V16h-7z M16.2,10.2h2.5l1.8,2.3h-4.3z" +
            " M1,16h21v1H1z" +
            " M5,19m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0 M18,19m-2,0a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        evenOdd = true,
    )
}

/** Koparka: gąsienice, kabina, wysięgnik z łyżką. */
private val Excavator: ImageVector by lazy {
    customIcon(
        "Excavator",
        "M3,17.5h11a2,2 0 0 1 0,4h-11a2,2 0 0 1 0,-4z" +
            " M3,11h4l1,-4h4v9H3z" +
            " M12,10l5,-6h1.5l-4.5,7.5V16h-2z" +
            " M18.5,4l3,4l-1,4.5l-3.5,-1.5l1.2,-2.5z",
    )
}

/** Quad: dwa duże koła, rama, siedzenie i kierownica. */
private val Quad: ImageVector by lazy {
    customIcon(
        "Quad",
        "M5.5,18m-3,0a3,3 0 1,0 6,0a3,3 0 1,0 -6,0 M18.5,18m-3,0a3,3 0 1,0 6,0a3,3 0 1,0 -6,0" +
            " M3,12h18l-2.5,2.6H5.5z" +
            " M6.5,9.5h6.5v2.5H6.5z" +
            " M15,7.5h5v1.4h-5z M16.6,8.9h1.4v3.1h-1.4z",
    )
}
