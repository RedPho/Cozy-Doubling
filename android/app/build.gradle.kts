import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

val localProps: Properties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// OAuth 2.0 *web* client ID used to request Google ID tokens. Empty in debug = mock Google tokens.
val googleWebClientId: String = (project.findProperty("googleWebClientId") as String?)
    ?: localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")

val releaseApiBaseUrl: String = (project.findProperty("releaseApiBaseUrl") as String?)
    ?: (project.findProperty("apiBaseUrl") as String?)
    ?: ""

// Fail release packaging early instead of shipping an app that points at a dev server or cannot
// sign in with Google. Debug builds are unaffected.
tasks.configureEach {
    // preReleaseBuild is the first task of every release variant (assembleRelease, bundleRelease).
    if (name == "preReleaseBuild") {
        // Plain local copies: the configuration cache cannot serialize build-script references.
        val apiUrl = releaseApiBaseUrl
        val webClientId = googleWebClientId
        doFirst {
            check(apiUrl.startsWith("https://")) {
                "Release builds need -PreleaseApiBaseUrl=https://... (got '$apiUrl')."
            }
            check(webClientId.isNotBlank()) {
                "Release builds need GOOGLE_WEB_CLIENT_ID (OAuth web client ID) in local.properties or -PgoogleWebClientId=..."
            }
        }
    }
}

android {
    namespace = "com.grepho.cozydoubling"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.grepho.cozydoubling"
        minSdk = 24
        targetSdk = 36
        // Play Console already has versionCode 9 / 1.1.0 (Supabase build, commit b949621).
        versionCode = 10
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        debug {
            // Local FastAPI backend (emulator loopback) unless overridden with -PapiBaseUrl=...
            val debugApiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8000"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            // Production API must be HTTPS; cleartext is only permitted in debug builds.
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Lets JVM unit tests pass inert Android stubs (e.g. an Activity for sign-in APIs).
        unitTests.isReturnDefaultValues = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    androidResources {
        localeFilters += listOf("en", "tr", "de", "fr", "es", "it")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Google Sign-In through Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
}

secrets {
    // Non-secret defaults (legal page URLs, empty client IDs) so a fresh clone and CI can build.
    // Real values go in local.properties, which overrides these and is never committed.
    defaultPropertiesFileName = "local.defaults.properties"
    // Generated explicitly above (the plugin emits invalid Java for empty values).
    ignoreList.add("GOOGLE_WEB_CLIENT_ID")
}
