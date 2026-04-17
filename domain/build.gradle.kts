plugins {
    alias(libs.plugins.pulse.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
