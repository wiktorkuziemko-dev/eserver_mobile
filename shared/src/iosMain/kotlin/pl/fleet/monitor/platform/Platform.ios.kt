package pl.fleet.monitor.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberPlatformActions(): PlatformActions = remember { IosActions }

private object IosActions : PlatformActions {
    override fun share(subject: String, text: String) {
        val top = topViewController() ?: return
        val vc = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        vc.setValue(subject, forKey = "subject")
        // Na iPadzie okno udostępniania musi mieć punkt zaczepienia.
        vc.popoverPresentationController?.sourceView = top.view
        top.presentViewController(vc, animated = true, completion = null)
    }

    override fun copyToClipboard(label: String, text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun openGoogleMaps(latitude: Double, longitude: Double) {
        val app = UIApplication.sharedApplication
        val native = NSURL.URLWithString("comgooglemaps://?q=$latitude,$longitude&center=$latitude,$longitude&zoom=16")
        val url = if (native != null && app.canOpenURL(native)) native else NSURL.URLWithString(googleMapsWebUrl(latitude, longitude))
        url?.let { app.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
    }

    override fun toast(message: String) = Unit
}

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

@Composable
actual fun StatusBarIcons(white: Boolean) {
    // iOS: kolor paska stanu ustawia kontroler widoku (MainViewController) – na razie domyślny.
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    androidx.compose.runtime.DisposableEffect(enabled) {
        platform.UIKit.UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { platform.UIKit.UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
