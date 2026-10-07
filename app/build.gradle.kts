plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.paul.sonoscontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.paul.sonoscontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    // Material Symbols (Rounded) für die frei wählbaren Speaker-Icons
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Custom Tabs für den OAuth-Login (empfohlener Weg statt WebView, siehe RFC 8252)
    implementation("androidx.browser:browser:1.8.0")

    // Verschlüsselte Ablage für Access-/Refresh-Token
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Netzwerk + JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Lokale Datenbank für Speaker-Konfiguration und App-Einstellungen
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Cover-Bilder laden
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Farben aus dem Cover für den Homescreen-Hintergrund
    implementation("androidx.palette:palette-ktx:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
