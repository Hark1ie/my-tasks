import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The build server passes the run number so every build installs over the previous one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

// Signing key and its password live in android/signing (see the README there).
val signingDir = rootProject.file("signing")
val signingProps = Properties().apply {
    val f = signingDir.resolve("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreFile = signingDir.resolve("release.keystore")
val hasReleaseKey = keystoreFile.exists() && signingProps.getProperty("storePassword") != null

android {
    namespace = "app.mytasks"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mytasks"
        minSdk = 32
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias") ?: "mytasks"
                keyPassword = signingProps.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            // Removes unused code and resources; the libraries ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Sync and sign-in
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
