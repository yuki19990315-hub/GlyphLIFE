plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.glyphlife.monsterquest"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.glyphlife.monsterquest"
        minSdk = 29
        targetSdk = 35
        versionCode = 50
        versionName = "6.0.0-preview.2"
    }
    buildFeatures { compose = true }
    defaultConfig { manifestPlaceholders["monsterQuestLabel"] = "Monster Quest" }
    buildTypes {
        getByName("debug") {
            // A fresh install side by side with the user's original debug
            // APK. Preserve the original applicationId on regular builds.
            if (providers.gradleProperty("monsterquestQaInstall").orNull == "true") {
                applicationIdSuffix = ".qa"
                manifestPlaceholders["monsterQuestLabel"] = "Monster Quest QA"
            }
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
