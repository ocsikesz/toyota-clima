plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "ro.ocsike.toyotaclima"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.ocsike.toyotaclima"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Aceeași cheie la fiecare build, ca APK-ul nou să se instaleze peste cel vechi.
    signingConfigs {
        create("shared") {
            storeFile = file("signing.keystore")
            storePassword = "toyotaclima"
            keyAlias = "toyotaclima"
            keyPassword = "toyotaclima"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
