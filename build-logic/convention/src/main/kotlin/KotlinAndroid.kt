import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Configure base Kotlin with Android options for Kotlin Android plugin. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

        defaultConfig {
            minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = false
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()
}

/** Configure base Kotlin for JVM libraries (pure Kotlin modules). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>()
}

private inline fun <reified T : org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension> Project.configureKotlin() {
    extensions.configure<T> {
        val warningsAsErrors: String? = providers.gradleProperty("warningsAsErrors").orNull
        when (this) {
            is KotlinAndroidProjectExtension -> compilerOptions
            is org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension -> compilerOptions
            else -> null
        }?.apply {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.FlowPreview")
        }
    }
}
