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
        // Google Play wymaga rosnącego versionCode. W CI: liczba minut od 1.01.2025 (UTC) –
        // zawsze rośnie i jest wyższa od dawnych numerów z GitHuba (numer przebiegu + przesunięcie).
        versionCode = if (System.getenv("CI") != null) {
            ((System.currentTimeMillis() / 1000 - 1_735_689_600L) / 60).toInt()
        } else 14
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
