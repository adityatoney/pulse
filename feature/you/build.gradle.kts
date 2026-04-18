plugins {
    alias(libs.plugins.pulse.android.feature)
}

android {
    namespace = "com.pulse.feature.you"
}

dependencies {
    implementation(project(":data"))
    implementation(libs.kotlinx.serialization.json)
}
