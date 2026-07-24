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
        versionCode = 13
        versionName = "0.13"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val youtubeDlAndroid = "0.18.1"

    implementation("io.github.junkfood02.youtubedl-android:library:$youtubeDlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubeDlAndroid")

    // Override old transitive versions exposed to untrusted extractor output.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.3")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.3")
    implementation("commons-io:commons-io:2.18.0")
}
