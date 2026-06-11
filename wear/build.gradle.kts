plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.usagecompanion.claude.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.usagecompanion.claude"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
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
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui-android:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
