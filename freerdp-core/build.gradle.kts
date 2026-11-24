plugins {
    id("com.android.library")
}

val freeRdpRoot = "../external/FreeRDP/client/Android/Studio/freeRDPCore"
val buildFreeRdpNative = providers.gradleProperty("INFINITY_BUILD_FREERDP_NATIVE").orNull == "true"

android {
    namespace = "com.freerdp.freerdpcore"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        if (buildFreeRdpNative) {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DWITH_OPENSSL=ON",
                        "-DWITH_FFMPEG=OFF",
                        "-DWITH_OPENH264=OFF",
                        "-DWITH_OPUS=OFF",
                        "-DWITH_CJSON=OFF",
                        "-DWITH_PNG=OFF",
                        "-DWITH_WEBP=OFF",
                        "-DWITH_JPEG=OFF"
                    )
                }
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDir("$freeRdpRoot/src/main/java")
            res.srcDir("$freeRdpRoot/src/main/res")
            assets.srcDir("$freeRdpRoot/src/main/assets")
            jniLibs.srcDir("$freeRdpRoot/src/main/jniLibs")
        }
    }

    if (buildFreeRdpNative) {
        externalNativeBuild {
            cmake {
                path = file("$freeRdpRoot/src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildFeatures {
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.collection:collection:1.4.5")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")
}
