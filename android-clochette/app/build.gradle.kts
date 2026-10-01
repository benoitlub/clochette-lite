plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.feuch.clochette"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.feuch.clochette"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("CLOCHETTE_VERSION_CODE").orNull?.toIntOrNull() ?: 40
        versionName = providers.environmentVariable("CLOCHETTE_VERSION_NAME").orNull ?: "0.1.40"
    }

    signingConfigs {
        create("clochetteRelease") {
            val storePath = providers.environmentVariable("CLOCHETTE_KEYSTORE_PATH").orNull
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("CLOCHETTE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CLOCHETTE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CLOCHETTE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("clochetteRelease")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
