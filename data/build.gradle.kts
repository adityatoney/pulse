import java.util.Properties

plugins {
    alias(libs.plugins.pulse.android.library)
    alias(libs.plugins.pulse.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.pulse.data"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Read from root local.properties so URLs/tokens aren't committed.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { stream -> load(stream) }
        }
        buildConfigField(
            "String",
            "GOOGLE_HEALTH_WEB_CLIENT_ID",
            "\"" + (localProps.getProperty("GOOGLE_HEALTH_WEB_CLIENT_ID") ?: "") + "\""
        )
        buildConfigField(
            "String",
            "GOOGLE_HEALTH_ANDROID_CLIENT_ID",
            "\"" + (localProps.getProperty("GOOGLE_HEALTH_ANDROID_CLIENT_ID") ?: "") + "\""
        )
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") { option("lite") }
                register("kotlin") { option("lite") }
            }
        }
    }
}

// Ensure protobuf-generated sources are compiled by KSP so Hilt can resolve proto types.
afterEvaluate {
    android.libraryVariants.forEach { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.named("ksp${capName}Kotlin", org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool::class.java) {
            dependsOn("generate${capName}Proto")
            source(layout.buildDirectory.dir("generated/source/proto/${variant.name}"))
        }
    }
}

dependencies {
    api(project(":domain"))

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (Preferences + Proto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)

    // WorkManager + Hilt Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Health Connect
    implementation(libs.androidx.health.connect)

    // Ktor (for Google Health REST stub + fault injection hook)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Credential Manager (Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
}
