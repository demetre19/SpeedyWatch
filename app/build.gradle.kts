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
        versionCode = 45
        versionName = "0.45"
        manifestPlaceholders["appLabel"] = "SpeedyWatch"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            // Opt-in side-by-side install next to an existing SpeedyWatch:
            // ./gradlew assembleDebug -PdevInstall
            // Default debug builds keep the release package name so they
            // update the installed app in place (same-machine debug key).
            if (findProperty("devInstall") != null) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-debug"
                manifestPlaceholders["appLabel"] = "SpeedyWatch Dev"
            }
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

    // Official compatibility API used to install the fixed media controller in HTTPS embeds.
    implementation("androidx.webkit:webkit:1.14.0")

    testImplementation("junit:junit:4.13.2")
}
