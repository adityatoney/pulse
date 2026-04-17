plugins {
    alias(libs.plugins.pulse.android.library)
    alias(libs.plugins.pulse.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pulse.core"
}

dependencies {
    api(project(":domain"))
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
}
