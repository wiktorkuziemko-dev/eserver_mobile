plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pl.fleet.monitor"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.fleet.monitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Do testów podpisujemy wersję release kluczem debug – przed publikacją w Google Play
            // należy skonfigurować własny keystore.
            signingConfig = signingConfigs.getByName("debug")
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
