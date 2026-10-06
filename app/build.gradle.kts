plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.airmode"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.airmode"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signingPath = providers.environmentVariable("AIRMODE_KEYSTORE").orNull
    if (signingPath != null) {
        signingConfigs.create("release") {
            storeFile = file(signingPath)
            storePassword = System.getenv("AIRMODE_STORE_PASSWORD")
            keyAlias = System.getenv("AIRMODE_KEY_ALIAS") ?: "airmode"
            keyPassword = System.getenv("AIRMODE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            versionNameSuffix = "-diagnostics"
            if (signingPath != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    constraints { implementation("androidx.graphics:graphics-path:1.1.0") { because("16 KB native library compatibility on current Pixel OS") } }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
