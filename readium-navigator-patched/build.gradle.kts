/*
 * Local fork of org.readium.kotlin-toolkit:readium-navigator:3.1.1's `readium-navigator` module,
 * vendored source-for-source (same `org.readium.r2.navigator` package names, so no app code needed
 * to change) so `R2FXLLayout` -- the fixed-layout/comic pinch-zoom-and-pan view -- can actually be
 * patched. It's `internal`, so it isn't subclassable or overridable from app code; forking was the
 * only way to fix the zoom-snap and double-tap-zoom bugs. See ReaderActivity.kt's comment atop the
 * comic-rendering section, and the manga_reader_zoom_chrome_bugs memory, for the bug context.
 *
 * Kept as a plain local module (no readium.library-conventions/maven-publish plugin, no
 * explicitApi()/allWarningsAsErrors) since this only ever needs to compile as part of this app,
 * never publish. Dependency versions below are copied from the upstream repo's own
 * gradle/libs.versions.toml at the 3.1.1 tag, not this app's catalog -- these are the versions the
 * vendored source was actually written against.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.readium.r2.navigator"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + ("-Xconsistent-data-class-copy-visibility")
    }
}

dependencies {
    api("org.readium.kotlin-toolkit:readium-shared:3.1.1")

    implementation(files("libs/PhotoView-2.3.0.jar"))

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.legacy:legacy-support-core-ui:1.0.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-session:1.6.0")
    implementation("androidx.media3:media3-common-ktx:1.6.0")
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.webkit:webkit:1.13.0")

    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jsoup:jsoup:1.18.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
