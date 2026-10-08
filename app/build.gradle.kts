import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Chiave di firma fissa (keystore.properties in locale, secret su GitHub Actions): stessa firma su ogni build,
// così gli aggiornamenti si installano sopra la versione precedente.
val ks = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }

android {
    namespace = "com.example.mp3"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.mp3"
        minSdk = 24
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 4  // su Actions cresce da solo a ogni build
        versionName = "1.3"
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(ks.getProperty("storeFile") ?: "keystore.jks")
            storePassword = ks.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = ks.getProperty("keyAlias") ?: "musica"
            keyPassword = ks.getProperty("keyPassword") ?: System.getenv("KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    testImplementation("junit:junit:4.13.2")
}
