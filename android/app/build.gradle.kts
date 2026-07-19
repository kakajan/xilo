import java.net.URI
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

abstract class CheckReleaseEndpoints : DefaultTask() {
    @get:Input
    abstract val apiBaseUrl: Property<String>

    @get:Input
    abstract val websocketBaseUrl: Property<String>

    @TaskAction
    fun verifyEndpoints() {
        validate(apiBaseUrl.orNull, "API base URL", "https")
        validate(websocketBaseUrl.orNull, "WebSocket base URL", "wss")
    }

    private fun validate(value: String?, label: String, requiredScheme: String) {
        if (value.isNullOrBlank()) {
            throw GradleException("$label is required for release. See android/README.md.")
        }
        val uri = runCatching { URI(value) }
            .getOrElse { throw GradleException("$label is not a valid URL. See android/README.md.") }
        if (!uri.scheme.equals(requiredScheme, ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw GradleException(
                "$label must use $requiredScheme with a valid host for release. " +
                    "See android/README.md."
            )
        }
        if (uri.userInfo != null) {
            throw GradleException(
                "$label must not contain embedded credentials. See android/README.md."
            )
        }
    }
}

val RELEASE_API_UNSET = "https://unset.xilo.invalid/"
val RELEASE_WS_UNSET = "wss://unset.xilo.invalid/ws"

fun projectOrEnv(gradleProperty: String, envVar: String): String? =
    (project.findProperty(gradleProperty) as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envVar)?.trim()?.takeIf { it.isNotEmpty() }

fun normalizeApiBaseUrl(url: String): String =
    if (url.endsWith("/")) url else "$url/"

fun requireReleaseSafe(url: String, label: String, requiredScheme: String): String {
    val uri = runCatching { URI(url) }
        .getOrElse { error("$label is not a valid URL. See android/README.md.") }
    if (!uri.scheme.equals(requiredScheme, ignoreCase = true) || uri.host.isNullOrBlank()) {
        error(
            "$label must use $requiredScheme with a valid host for release. " +
                "See android/README.md."
        )
    }
    if (uri.userInfo != null) {
        error("$label must not contain embedded credentials. See android/README.md.")
    }
    return url
}

android {
    namespace = "ir.xilo.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "ir.xilo.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val apiBase = normalizeApiBaseUrl(
                projectOrEnv("xilo.apiBaseUrl", "XILO_API_BASE_URL")
                    ?: "http://10.0.2.2:8888/"
            )
            val wsBase = projectOrEnv("xilo.wsBaseUrl", "XILO_WS_BASE_URL")
                ?: "ws://10.0.2.2:8888/ws"
            buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
            buildConfigField("String", "WS_BASE_URL", "\"$wsBase\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val apiBase = normalizeApiBaseUrl(
                projectOrEnv("xilo.apiBaseUrl", "XILO_API_BASE_URL")
                    ?.let { requireReleaseSafe(it, "API base URL", "https") }
                    ?: RELEASE_API_UNSET
            )
            val wsBase = projectOrEnv("xilo.wsBaseUrl", "XILO_WS_BASE_URL")
                ?.let { requireReleaseSafe(it, "WebSocket base URL", "wss") }
                ?: RELEASE_WS_UNSET
            buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
            buildConfigField("String", "WS_BASE_URL", "\"$wsBase\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation("io.mockk:mockk:1.13.13")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Retrofit & OkHttp & Serialization
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.kotlinx.serialization.json)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)
  androidTestImplementation("androidx.room:room-testing:2.6.1")

  // Durable outbox scheduling
  implementation("androidx.work:work-runtime:2.11.2")

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  compileOnly("com.google.errorprone:error_prone_annotations:2.50.0")

  // UI Utilities (Coil & Paging & Icons)
  implementation(libs.coil.compose)
  implementation(libs.paging.runtime)
  implementation(libs.paging.compose)
  implementation(libs.iconsax.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.register<CheckReleaseEndpoints>("checkReleaseEndpoints") {
    group = "verification"
    description =
        "Fails release packaging when API/WS endpoints are unset or insecure"
    apiBaseUrl.set(
        providers.gradleProperty("xilo.apiBaseUrl")
            .orElse(providers.environmentVariable("XILO_API_BASE_URL"))
            .orElse("")
    )
    websocketBaseUrl.set(
        providers.gradleProperty("xilo.wsBaseUrl")
            .orElse(providers.environmentVariable("XILO_WS_BASE_URL"))
            .orElse("")
    )
}

listOf(
    "assembleRelease",
    "bundleRelease",
    "minifyReleaseWithR8",
    "packageRelease",
).forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        dependsOn("checkReleaseEndpoints")
    }
}

tasks.register<Exec>("launchDebug") {
    group = "install"
    description = "Install debug APK and launch MainActivity on a connected device/emulator"
    dependsOn("installDebug")

    val adbExecutable = System.getenv("ANDROID_HOME")?.let { File(it, "platform-tools/adb") }
        ?.takeIf { it.exists() }
        ?.absolutePath
        ?: "adb"

    commandLine(
        adbExecutable,
        "shell",
        "am",
        "start",
        "-n",
        "ir.xilo.app/.MainActivity"
    )
}
