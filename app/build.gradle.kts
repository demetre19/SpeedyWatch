plugins {
    id("com.android.application")
}

android {
    namespace = "com.speedywatch.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.speedywatch.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 10
        versionName = "0.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
