plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hermes.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hermes.mobile"
        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    // API 36 ile uyumlu Compose sürümleri
    implementation(
        platform("androidx.compose:compose-bom:2024.12.01")
    )

    implementation("androidx.activity:activity-compose:1.10.0")

    implementation("androidx.compose.material3:material3")

    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.compose.ui:ui")

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    implementation(
        "com.squareup.okhttp3:okhttp:5.1.0"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )
}
