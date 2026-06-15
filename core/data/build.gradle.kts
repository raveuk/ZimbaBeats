plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zimbabeats.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Enable Java 8+ API desugaring for NewPipe Extractor compatibility on older devices
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    // Core Library Desugaring with NIO - enables Java 8+ APIs including Charset-based URLDecoder on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    // Module dependencies
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation("androidx.annotation:annotation:1.9.1")

    // WebView (used for BotGuard/PoToken generation to fix YouTube stream 403s)
    implementation("androidx.webkit:webkit:1.12.1")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // OkHttp (for NewPipe downloader)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.core)

    // HTML parsing for YouTube scraping
    implementation("org.jsoup:jsoup:1.18.1")

    // NewPipe Extractor for YouTube stream extraction (handles n parameter decryption)
    // Using v0.25.1 with protobuf exclusions to avoid Firebase conflicts
    implementation(libs.newpipe.extractor) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // youtubedl-android (yt-dlp bundled with Python runtime) — third-tier fallback
    // when both InnerTube and NewPipe fail. Adds ~12 MB per ABI (Python interpreter).
    // Source: https://github.com/yausername/youtubedl-android
    // `api` so the app module can call YoutubeDL.getInstance().init(context) at startup.
    api("io.github.junkfood02.youtubedl-android:library:0.18.1")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
