pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TapCard"
include(":app")

// Pure-Kotlin NFC tag emulation, testable without the Android SDK: `cd nfc-core && gradle test`
includeBuild("nfc-core")
