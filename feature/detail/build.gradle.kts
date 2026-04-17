plugins {
    alias(libs.plugins.pulse.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pulse.feature.detail"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
}
