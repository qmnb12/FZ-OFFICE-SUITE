// Code On The Go keeps Kotlin's Gradle plugin available in its on-device
// Maven repository. AGP 9.x provides built-in Kotlin compilation, so the
// application module does not apply the legacy org.jetbrains.kotlin.android plugin.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
}
