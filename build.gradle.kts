plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Keep failures visible in the check summary when downloadable CI logs are unavailable.
gradle.buildFinished {
    failure?.let { error ->
        println("::error::" + error.stackTraceToString().replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
    }
}
