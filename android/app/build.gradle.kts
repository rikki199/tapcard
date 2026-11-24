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
        versionCode = 7
        versionName = "1.6"
    }

    // Sign with the app's own permanent key when it's available, so new versions install as
    // updates over old ones. The key file is created by the GitHub workflow and protected by the
    // TAPCARD_KEY_PASSWORD repository secret (the workflow passes its SHA-256 hash as
    // TAPCARD_KEYSTORE_PASSWORD); without it, builds use a temporary debug key.
    val keyPassword = System.getenv("TAPCARD_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val keyFile = rootProject.file("tapcard-release.keystore")
    val permanentKey = keyPassword != null && keyFile.exists()
    signingConfigs {
        if (permanentKey) create("tapcard") {
            storeFile = keyFile
            storePassword = keyPassword
            keyAlias = "tapcard"
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        debug {
            if (permanentKey) signingConfig = signingConfigs.getByName("tapcard")
        }
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
