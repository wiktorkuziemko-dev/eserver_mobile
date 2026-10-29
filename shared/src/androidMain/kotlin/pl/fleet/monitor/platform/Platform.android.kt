package pl.fleet.monitor.platform

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { AndroidActions(context) }
}

private class AndroidActions(private val context: Context) : PlatformActions {
    override fun share(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, subject))
    }

    override fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("Skopiowano: $label")
    }

    override fun openGoogleMaps(latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsWebUrl(latitude, longitude)))
        runCatching { context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps")) }
            .recoverCatching { context.startActivity(intent) }
            .onFailure { toast("Nie można otworzyć Google Maps") }
    }

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
actual fun StatusBarIcons(white: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(white) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !white
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
}
