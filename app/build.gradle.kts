plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.org.retallack.slimbook"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.org.retallack.slimbook"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/keystores/slimbook-release.jks")
            storePassword = System.getenv("SLIMBOOK_KEYSTORE_PASSWORD") ?: "slimbook-release"
            keyAlias = "slimbook"
            keyPassword = System.getenv("SLIMBOOK_KEY_PASSWORD") ?: "slimbook-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
