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
        // Readium's Kotlin toolkit publishes to Maven Central, but some of its adapter
        // artifacts (e.g. pdfium) have historically needed JitPack as a fallback source.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Ishi Reader"
include(":app")
include(":readium-navigator-patched")
