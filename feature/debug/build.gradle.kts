plugins {
    alias(libs.plugins.pulse.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pulse.feature.debug"
}

dependencies {
    implementation(project(":data"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
}
