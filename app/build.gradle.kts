plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Versionierung über CI: Tag "v1.2.3" → versionName, Run-Nummer → versionCode.
val ciVersionName: String? = System.getenv("LOCUS_VERSION_NAME")
val ciVersionCode: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Release-Signatur kommt ausschließlich aus Umgebungsvariablen (GitHub Secrets).
val releaseKeystore: String? = System.getenv("LOCUS_KEYSTORE_PATH")

android {
    namespace = "io.github.mario2712.locus"
    compileSdk {
        // Compose 1.13 verlangt mindestens Android-API 37.1 (Minor-SDK-Release).
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.mario2712.locus"
        minSdk = 31
        targetSdk = 37
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "0.1.0-dev"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LOCUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LOCUS_KEY_ALIAS")
                keyPassword = System.getenv("LOCUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // Neuere Bibliotheksversionen werden bewusst gesteuert, nicht per Lint erzwungen.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
}
