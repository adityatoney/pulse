plugins {
    alias(libs.plugins.pulse.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pulse.feature.insights"
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
}
