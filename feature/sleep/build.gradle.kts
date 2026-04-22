plugins {
    alias(libs.plugins.pulse.android.feature)
}

android {
    namespace = "com.pulse.feature.sleep"
}

dependencies {
    implementation(project(":data"))
    implementation(libs.kotlinx.serialization.json)
}
