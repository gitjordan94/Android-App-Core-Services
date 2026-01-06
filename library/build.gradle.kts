plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.core.services"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SDK_VERSION",
            "\"1.4.4\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

//noinspection UseTomlInstead
dependencies {
    // Core
    api("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Compose
    implementation("androidx.compose.ui:ui:1.10.0")

    // Lifecycle
    val lifecycleVersion = "2.10.0"
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Google Play Services
    implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")
    implementation("com.google.android.gms:play-services-appset:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Analytics
    implementation("com.amplitude:analytics-android:1.22.4")
    implementation("com.amplitude:plugin-session-replay-android:0.22.1")
    api("com.appsflyer:af-android-sdk:6.17.5")

    // Firebase
    api(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")

    // ATT
    implementation("com.android.installreferrer:installreferrer:2.2")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.firebase:firebase-crashlytics-buildtools:3.0.6")

    // Network
    val ktorVersion = "3.3.3"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}