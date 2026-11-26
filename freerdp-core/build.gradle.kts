plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val freeRdpRoot = "../external/FreeRDP/client/Android/Studio/freeRDPCore"
val buildFreeRdpNative = providers.gradleProperty("INFINITY_BUILD_FREERDP_NATIVE").orNull == "true"

android {
    // Pinned upstream FreeRDP Android sources contain legacy lint findings.
    // App lint remains strict; keep the third-party module report visible without failing the app gate.
    lint {
        abortOnError = false
    }
    namespace = "com.freerdp.freerdpcore"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndkVersion = "27.0.12077973"
        externalNativeBuild {
            cmake {
            }
        }
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
        viewBinding = true
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

val prepareFreeRdpJavaSources = tasks.register<Sync>("prepareFreeRdpJavaSources") {
    from("$freeRdpRoot/src/main/java")
    // INFINITY supplies its own GlobalApp and does not expose FreeRDP's sample print UI.
    // Exclude the unused sample print pipeline so Android 13 POST_NOTIFICATIONS is not
    // requested solely for an unshipped feature.
    exclude("**/GlobalApp.java")
    exclude("**/presentation/PrintNotificationHelper.java")
    exclude("**/presentation/PrintJobMonitor.java")
    exclude("**/presentation/PrintProxyActivity.java")
    // Replace upstream WebView and intent handlers with locally hardened equivalents.
    exclude("**/presentation/HelpActivity.java")
    exclude("**/services/SessionRequestHandlerActivity.java")
    into(layout.buildDirectory.dir("generated/freerdp/java"))
}

android.sourceSets.getByName("main").java.srcDir(
    layout.buildDirectory.dir("generated/freerdp/java")
)

tasks.configureEach {
    if (name.startsWith("compile") &&
        (name.endsWith("Kotlin") || name.endsWith("JavaWithJavac"))) {
        dependsOn(prepareFreeRdpJavaSources)
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
