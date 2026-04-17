plugins {
    alias(libs.plugins.pulse.android.feature)
}

android {
    namespace = "com.pulse.feature.dashboard"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
