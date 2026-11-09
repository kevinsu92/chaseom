import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

// Release signing: read from keystore.properties (git-ignored). See keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

// App name (CEO 2026-11-09: 차섬 / Chaseom), read from gradle.properties as UTF-8 so the Korean label
// survives (Gradle itself reads gradle.properties as ISO-8859-1). app.label = launcher label;
// release.appName = APK file name <appName>_<appName>_release.apk via scripts/build-release.ps1.
val identityProps = Properties().apply {
    rootProject.file("gradle.properties").reader(Charsets.UTF_8).use { load(it) }
}
val appLabel: String = (identityProps.getProperty("app.label") ?: identityProps.getProperty("release.appName"))
    .orEmpty().trim().ifEmpty { "app" }

android {
    namespace = "app.chaseom"
    // Compose BOM 2026.09 requires compileSdk >= 37. targetSdk stays 34 (Pleos Connect = API 34).
    compileSdk = 37

    defaultConfig {
        // Package name carries no real name or school (HQ).
        applicationId = "app.chaseom"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.property("app.versionCode") as String).toInt()
        versionName = project.property("app.versionName") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", appLabel)
    }

    // AAOS Car API (CarUxRestrictionsManager), provided by the platform at runtime.
    useLibrary("android.car")

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Debug builds may fall back to scripted fake vehicle data when no Pleos service is present.
            buildConfigField("boolean", "FAKE_DATA_ALLOWED", "true")
            // Weather island face: on in debug for evaluation; release stays off until QA 11-12 PASS.
            buildConfigField("boolean", "KICK_ISLAND", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Review guideline: no dummy/temp data in release. Release always uses the real Pleos SDK.
            buildConfigField("boolean", "FAKE_DATA_ALLOWED", "false")
            // QA 11-12 conditional PASS: island on in release. -Prelease.island=false builds the OFF
            // comparison APK (QA release checks) or the fallback if a release check fails.
            buildConfigField("boolean", "KICK_ISLAND", (findProperty("release.island") ?: "true").toString())
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.pleos.vehicle)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
