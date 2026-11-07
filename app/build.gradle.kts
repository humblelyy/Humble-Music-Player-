
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "app.myspoti.engine"
    compileSdk = 37

    // Two channels that install side by side.
    val version = (findProperty("appVersionName") as String?) ?: "2.3.0"
    val beta = findProperty("appVersionName") == null || version.contains("-beta")

    defaultConfig {
        applicationId = if (beta) "app.myspoti" else "app.myspoti.stable"
        manifestPlaceholders["appLabel"] = "HUMBLE MUSIC"
        manifestPlaceholders["cliEnabled"] = beta.toString()

        minSdk = 29
        targetSdk = 36

        versionCode =
            (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 200
        versionName = version

        buildConfigField(
            "String",
            "UPDATE_REPO",
            "\"${(findProperty("updateRepo") as String?) ?: System.getenv("MYSPOTI_UPDATE_REPO") ?: ""}\""
        )

        // ABI filters are configured in splits below.
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Generate separate APKs for ARM64 and ARM32.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Release signing configuration.
    val envStore = System.getenv("MYSPOTI_KEYSTORE")
    val hasReleaseKey =
        envStore != null || keystorePropertiesFile.exists()

    val stableRelease = !beta && findProperty("overDev") == null

    if (
        stableRelease &&
        !hasReleaseKey &&
        gradle.startParameter.taskNames.any {
            Regex("(assemble|bundle|package|install)Release").containsMatchIn(it)
        }
    ) {
        throw GradleException(
            "A stable release needs the release key: set MYSPOTI_KEYSTORE (CI) or add keystore/keystore.properties."
        )
    }

    signingConfigs {
        create("release") {
            if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("MYSPOTI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MYSPOTI_KEY_ALIAS")
                keyPassword = System.getenv("MYSPOTI_KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(
                    "keystore/${keystoreProperties.getProperty("storeFile")}"
                )
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Humble Music"
            manifestPlaceholders["cliEnabled"] = "true"
        }

        release {
            signingConfig = signingConfigs.getByName(
                if (hasReleaseKey) "release" else "debug"
            )

            if (findProperty("overDev") != null) {
                applicationIdSuffix = ".dev"
                manifestPlaceholders["appLabel"] = "Humble Music"
            }

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (!beta) {
                proguardFiles("proguard-strip-logs.pro")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":host-android"))
    implementation(project(":extensions"))

    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    debugImplementation(libs.compose.ui.tooling)

    // Native UI
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Frosted UI
    implementation("dev.chrisbanes.haze:haze:1.3.1")

    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(libs.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)

    // Media playback
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource:1.8.0")
    implementation("androidx.media3:media3-database:1.8.0")

    // Baseline profiles
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // YouTube music videos
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    implementation(libs.okhttp)
}
