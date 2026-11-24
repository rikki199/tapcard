plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.tapcard"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tapcard"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // The app shows the same screens as the website: bundle ../../web as the app's assets.
    sourceSets["main"].assets.srcDirs("../../web")
}

dependencies {
    implementation("app.tapcard:nfc-core:1.0")
}
