import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.io.encoding.ExperimentalEncodingApi",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            api("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("com.squareup.okio:okio:3.18.2")
            api("com.russhwolf:multiplatform-settings:1.3.0")
            // Mapa OpenStreetMap w Compose – wspólna dla Androida i iOS
            implementation("ovh.plrapps:mapcompose-mp:1.1.5")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
            implementation("androidx.core:core-ktx:1.16.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.1")
        }
    }
}

android {
    namespace = "pl.fleet.monitor.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
