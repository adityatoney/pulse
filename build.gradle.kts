// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.protobuf) apply false
}

// Redirect per-module build/ output to external SSD when `externalBuildDir` is
// set in gradle.properties. Leave unset (or comment out) to keep ./build folders
// alongside each module (default Gradle behaviour).
val externalBuildRoot: String? = providers.gradleProperty("externalBuildDir").orNull
if (!externalBuildRoot.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(
            file("$externalBuildRoot/${rootProject.name}/${project.path.replace(":", "/").removePrefix("/")}/build")
        )
    }
}
