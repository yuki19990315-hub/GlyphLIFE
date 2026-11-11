plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val nothingSdk = file("libs/glyph-matrix-sdk-2.0.aar")

android {
    namespace = "dev.glyphlife.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.glyphlife.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"
        // AOD Toy is only offered in explicit Nothing SDK builds.
        resValue("bool", "glyph_aod_enabled", nothingSdk.exists().toString())
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets {
        getByName("main") {
            java.srcDir(if (nothingSdk.exists()) "src/glyph/java" else "src/stub/java")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    if (nothingSdk.exists()) { implementation(files(nothingSdk)) }
}
