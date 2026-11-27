
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.infinity.core"
    compileSdk = 35

    defaultConfig {
        val remoteAcceptanceBuild = providers.gradleProperty("INFINITY_REMOTE_ACCEPTANCE_BUILD").orNull == "true"
        val remoteAcceptanceExpiresAt = providers.gradleProperty("INFINITY_REMOTE_ACCEPTANCE_EXPIRES_AT_EPOCH").orNull?.toLongOrNull() ?: 0L
        buildConfigField("boolean", "INFINITY_REMOTE_ACCEPTANCE_BUILD", remoteAcceptanceBuild.toString())
        buildConfigField("long", "INFINITY_REMOTE_ACCEPTANCE_EXPIRES_AT_EPOCH", "${remoteAcceptanceExpiresAt}L")
        applicationId = "com.infinity.core"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    val releaseSigningValues = listOf(
        System.getenv("INFINITY_KEYSTORE_FILE"),
        System.getenv("INFINITY_KEYSTORE_PASSWORD"),
        System.getenv("INFINITY_KEY_ALIAS"),
        System.getenv("INFINITY_KEY_PASSWORD")
    )
    if (releaseSigningValues.all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("INFINITY_KEYSTORE_FILE")!!)
                storePassword = System.getenv("INFINITY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("INFINITY_KEY_ALIAS")
                keyPassword = System.getenv("INFINITY_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(project(":freerdpCore"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
