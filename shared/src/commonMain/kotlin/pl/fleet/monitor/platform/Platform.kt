package pl.fleet.monitor.platform

import androidx.compose.runtime.Composable

/** Czynności zależne od systemu (Android / iOS). */
interface PlatformActions {
    /** Systemowe okno „Udostępnij”. */
    fun share(subject: String, text: String)

    /** Kopiuje tekst do schowka (z krótkim potwierdzeniem, jeśli system je wspiera). */
    fun copyToClipboard(label: String, text: String)

    /** Otwiera pozycję w aplikacji Google Maps, a gdy jej brak – w przeglądarce. */
    fun openGoogleMaps(latitude: Double, longitude: Double)

    /** Krótki komunikat (Android: Toast; iOS: brak odpowiednika – ignorowany). */
    fun toast(message: String)
}

@Composable
expect fun rememberPlatformActions(): PlatformActions

/** Ustawia kolor ikon paska stanu: białe (na gradiencie) albo ciemne (na jasnym tle). */
@Composable
expect fun StatusBarIcons(white: Boolean)

internal fun googleMapsWebUrl(latitude: Double, longitude: Double) =
    "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
