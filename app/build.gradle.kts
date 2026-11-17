plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.evorez.ezdmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.evorez.ezdmanager"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Temporary CI diagnostics: surface build output as a check annotation when logs are inaccessible.
val ciDiagnostic = StringBuilder()
val captureDiagnostic = org.gradle.api.logging.StandardOutputListener { text ->
    synchronized(ciDiagnostic) { ciDiagnostic.append(text); if (ciDiagnostic.length > 30000) ciDiagnostic.delete(0, ciDiagnostic.length - 30000) }
}
logging.apply {
    addStandardOutputListener(captureDiagnostic)
    addStandardErrorListener(captureDiagnostic)
}
gradle.buildFinished { result ->
    if (result.failure != null) {
        val output = synchronized(ciDiagnostic) { ciDiagnostic.toString().takeLast(20000) }
        println("::error::" + (result.failure.toString() + "\n" + output).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
    }
}
