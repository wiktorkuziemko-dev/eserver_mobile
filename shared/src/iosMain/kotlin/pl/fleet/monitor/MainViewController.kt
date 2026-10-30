package pl.fleet.monitor

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** Punkt wejścia dla iOS – wywoływany z aplikacji SwiftUI (iosApp). */
@OptIn(ExperimentalSettingsImplementation::class)
fun MainViewController(): UIViewController {
    AppGraph.init(
        AppContainer(
            settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
            // Login, hasło i skrót MD5 trafiają do pęku kluczy iOS (null, gdy pęk kluczy jest niedostępny).
            secureSettings = KeychainSettings(service = "pl.fleet.monitor").takeIf { keychain ->
                runCatching { keychain.getStringOrNull("probe") }.isSuccess
            },
            appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "",
            cacheDir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String,
        ),
    )
    return ComposeUIViewController { App() }
}
