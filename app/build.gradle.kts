plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.aircontrol.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.aircontrol.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    val ksPath = System.getenv("AC_KEYSTORE")
    if (ksPath != null) signingConfigs.create("rel") {
        storeFile = file(ksPath); storePassword = System.getenv("AC_KS_PASS"); keyAlias = "aircontrol"; keyPassword = System.getenv("AC_KS_PASS")
    }
    buildTypes { release { isMinifyEnabled = false; isDebuggable = false; if (ksPath != null) signingConfig = signingConfigs.getByName("rel") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "task" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}
