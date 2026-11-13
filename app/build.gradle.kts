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

// App name (차섬 / Chaseom), read from gradle.properties as UTF-8 so the Korean label
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
        // Package name carries no personal name.
        applicationId = "app.chaseom"
        minSdk = 28
        targetSdk = 34
        versionCode = (project.property("app.versionCode") as String).toInt()
        versionName = project.property("app.versionName") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", appLabel)
        // The rear-seat mapping is checked per Pleos SDK version (cabin/Occupancy.kt).
        buildConfigField("String", "PLEOS_VEHICLE_SDK", "\"${libs.versions.pleosVehicle.get()}\"")
        // Round 2 (d): use the car's outside temperature at every temperature (EnergyModel OutsideMode.FULL).
        // Off by default (build G rule: only colder than -10 C). -Poutside.full=true turns it on.
        buildConfigField("boolean", "OUTSIDE_FULL", (findProperty("outside.full") ?: "false").toString())
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
            // Weather island face: on in debug.
            buildConfigField("boolean", "KICK_ISLAND", "true")
            buildConfigField("boolean", "KICK_RAIN", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Review guideline: no dummy/temp data in release. Release always uses the real Pleos SDK.
            buildConfigField("boolean", "FAKE_DATA_ALLOWED", "false")
            // Island on in release. -Prelease.island=false builds an island-off
            // comparison or fallback APK.
            buildConfigField("boolean", "KICK_ISLAND", (findProperty("release.island") ?: "true").toString())
            // Far rain: on unless -Prelease.rain=false.
            buildConfigField("boolean", "KICK_RAIN", (findProperty("release.rain") ?: "true").toString())
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

// Platform review E-1 (round 2): a release APK must not ship with OUTSIDE_FULL on while the README and the
// proposal say "outside temperature only when colder than -10 C". Any Release task with -Poutside.full=true fails.
// Turning (d) on for release is a deliberate change: drop this check together with the README/proposal update.
val outsideFullRequested = (findProperty("outside.full") ?: "false").toString().toBoolean()
gradle.taskGraph.whenReady {
    val releaseTasks = allTasks.filter { it.project == project && it.name.contains("Release") }
    if (outsideFullRequested && releaseTasks.isNotEmpty()) {
        throw GradleException(
            "outside.full=true is not allowed in a release build (platform E-1): ${releaseTasks.first().name}",
        )
    }
}
