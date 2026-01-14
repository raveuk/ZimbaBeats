plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlinx.coroutines.core)

    // Koin
    implementation(libs.koin.core)

    // Testing
    testImplementation("junit:junit:4.13.2")
}
