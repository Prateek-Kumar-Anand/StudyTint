import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

// Version comes from version.properties so an update is a one-file change.
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
// CI builds add the run number, so every CI build is newer than the previous one and installs over it.
val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
android {
    namespace = "com.student.smartstudy"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.student.smartstudy"
        minSdk = 24; targetSdk = 34
        versionCode = versionProps.getProperty("VERSION_CODE").trim().toInt() * 10000 + ciBuild
        versionName = versionProps.getProperty("VERSION_NAME").trim()
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    // A fixed signing key keeps every build installable over the previous one (updates keep your data).
    signingConfigs {
        create("studytint") {
            storeFile = file("studytint.jks")
            storePassword = "studytint"
            keyAlias = "studytint"
            keyPassword = "studytint"
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("studytint") }
        release {
            signingConfig = signingConfigs.getByName("studytint")
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    lint { abortOnError = false }
}
chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("python3")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
