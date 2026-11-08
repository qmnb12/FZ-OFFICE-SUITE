plugins {
    id("com.android.application")
}

android {
    namespace = "com.fz.office"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fz.office"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.24.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
