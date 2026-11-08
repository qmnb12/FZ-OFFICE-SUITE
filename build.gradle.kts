// Code On The Go (COTG) currently pins Kotlin's Gradle plugin in its
// on-device Maven repository. Keeping this classpath available also satisfies
// COTG's injected build tooling, even though this starter app is Java-only.
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
