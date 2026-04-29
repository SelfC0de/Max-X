plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "ru.maxx.app"
    compileSdk = 35

    defaultConfig {
        applicationId   = "ru.maxx.app"
        minSdk          = 21
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"
        buildConfigField("String", "APP_VERSION", "\"26.14.1\"")
        buildConfigField("int",    "APP_BUILD",   "6686")
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable         = true
            applicationIdSuffix  = ".debug"
            versionNameSuffix    = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    lint {
        // Баг в AGP 8.7.x + Kotlin 2.x — NonNullableMutableLiveDataDetector крашит lint
        disable += "NullSafeMutableLiveData"
        // Не ронять сборку из-за lint предупреждений
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.activity.compose)

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.coroutines)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore)
    implementation(libs.msgpack)
    implementation(libs.lz4)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.biometric)
    implementation(libs.security.crypto)
    implementation(libs.work.runtime)
    implementation(libs.core.splashscreen)
}
