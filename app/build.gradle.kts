plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pl.fleet.monitor"
    compileSdk = 37

    defaultConfig {
        // Identyfikator wpisu E-SERVER mini w Google Play (aktualizacja dotychczasowej aplikacji).
        applicationId = "com.eserver_mini"
        minSdk = 26
        targetSdk = 36
        // Każde wydanie z GitHuba ma kolejny numer (Google Play wymaga rosnącego versionCode).
        // Przesunięcie (zmienna ANDROID_VERSION_CODE_OFFSET w GitHubie) musi zapewnić numer wyższy niż w poprzedniej wersji w sklepie.
        val offset = System.getenv("ANDROID_VERSION_CODE_OFFSET")?.toIntOrNull() ?: 100
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(offset) ?: 14
        versionName = "2.2.0"
    }

    // Klucz przesyłania do Google Play – z sekretów GitHuba (ścieżka do pliku i hasła w zmiennych środowiskowych).
    val uploadKeystore = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Klucz przesyłania, gdy jest skonfigurowany; w przeciwnym razie klucz testowy (tylko do instalacji APK).
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
