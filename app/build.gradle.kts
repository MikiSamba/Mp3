import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.mp3"
        minSdk = 24
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 7  // su Actions cresce da solo a ogni build
        versionName = "1.8"
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
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    testImplementation("junit:junit:4.13.2")
}
