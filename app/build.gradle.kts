import java.util.Properties
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.readtome.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dellanio.readtome"
        minSdk = 30 // API 30+ para suporte total a takeScreenshot no AccessibilityService
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        val buildTimestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTimestamp\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                if (!storeFilePath.isNullOrBlank()) {
                    storeFile = rootProject.file(storeFilePath)
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Ativar true se desejar R8/ProGuard completo
            if (keystorePropertiesFile.exists() && !keystoreProperties.getProperty("storeFile").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Media & Audio
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)

    // ML Kit OCR
    implementation(libs.mlkit.text.recognition)

    // Web Scraping & Readability
    implementation(libs.readability4j)
    implementation(libs.jsoup)

    // Gemini SDK
    implementation(libs.google.generativeai)
}
