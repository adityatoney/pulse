import java.util.Properties

plugins {
    alias(libs.plugins.pulse.android.application)
    alias(libs.plugins.pulse.android.application.compose)
    alias(libs.plugins.pulse.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pulse"

    defaultConfig {
        applicationId = "com.pulse"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mapsKey: String = project.findProperty("MAPS_API_KEY")?.toString()
            ?: run {
                val props = Properties()
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { props.load(it) }
                props.getProperty("MAPS_API_KEY", "")
            }
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("boolean", "DEBUG_MENU_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "DEBUG_MENU_ENABLED", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:debug"))
    implementation(project(":feature:exercise"))
    implementation(project(":feature:you"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:sleep"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
