plugins {
    alias(libs.plugins.pulse.android.feature)
}

android {
    namespace = "com.pulse.feature.exercise"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.health.connect)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
}
