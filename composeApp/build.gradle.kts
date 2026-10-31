import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serializerCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "tj.app.quran_todo.compose")
            export(libs.calf.ui)
            linkerOpts.add("-lsqlite3")
            linkerOpts.add("-framework")
            linkerOpts.add("Speech")
            linkerOpts.add("-framework")
            linkerOpts.add("UserNotifications")
        }
    }
    
    sourceSets {
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.navigation.compose)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            // Downloads and storage (task D3): a worker that survives the app and resumes after a break.
            implementation(libs.androidx.work.runtime)
            // The "Lesson of the day" home screen widget (task F5b).
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)

            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.ktor.client.okhttp)
            implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.calf.ui)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.viewmodel)

            implementation(libs.bundles.ktor)
            implementation(libs.decompose)
            implementation(libs.decompose.compose)

//            implementation(libs.navigation3.runtime)

            implementation(libs.material.icons.core)
            implementation(libs.material.icons.extended)


        }

        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
        }

        // The Hijri dates of Ramadan are checked against ICU4J, the library that `android.icu` is made from (the stub of android.jar
        // does not work in plain JVM tests).
        androidUnitTest.dependencies {
            implementation("com.ibm.icu:icu4j:76.1")
        }

        // Instrumented tests (need a device or an emulator): ./gradlew :composeApp:connectedDebugAndroidTest
        // The migration test lives here: Room's MigrationTestHelper reads the exported schemas from the assets.
        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.junit)
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.room:room-testing:${libs.versions.room.get()}")
        }
    }
}

// Release signing comes from local.properties (release.storeFile, release.storePassword, release.keyAlias,
// release.keyPassword) or, for CI, from the environment (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
// RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD). When any of them is missing the release is built unsigned and
// the Gradle configuration still succeeds. Never write the values into a tracked file: see docs/RELEASE.md.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSetting(property: String, environment: String): String? =
    (localProperties.getProperty(property) ?: System.getenv(environment))?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFile: File? = releaseSetting("release.storeFile", "RELEASE_STORE_FILE")
    ?.let { path -> rootProject.file(path) }
    ?.takeIf { file ->
        file.isFile.also { exists ->
            if (!exists) logger.warn("Release signing: the key store ${file.path} does not exist, building unsigned.")
        }
    }
val releaseStorePassword: String? = releaseSetting("release.storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = releaseSetting("release.keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = releaseSetting("release.keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    // The code and manifest entries of the voice check (src/voiceproto) belong to the debug build only when the module is included.
    if (findProject(":voiceproto") != null) {
        sourceSets.getByName("debug") {
            java.srcDir("src/voiceproto/kotlin")
            manifest.srcFile("src/voiceproto/AndroidManifest.xml")
        }
    }
    namespace = "tj.app.quran_todo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tj.app.quran_todo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 7
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        // android.util.Log and friends return defaults in local unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        // Debug builds stay out of the production Firebase project (see AppTelemetry.android.kt).
        getByName("debug") {
            manifestPlaceholders["analyticsDeactivated"] = "true"
            manifestPlaceholders["crashlyticsEnabled"] = "false"
        }
        getByName("release") {
            manifestPlaceholders["analyticsDeactivated"] = "false"
            manifestPlaceholders["crashlyticsEnabled"] = "true"
            // R8: shrinks and obfuscates the code, shrinkResources drops unused resources. The keep rules are in
            // proguard-rules.pro; the Crashlytics plugin uploads the mapping file for readable stack traces.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        // java.time (kotlinx-datetime, the reminder) is missing below API 26; minSdk is 24.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    // F7 prototype: whisper.cpp and the PCM recorder. Debug only and only with -Pvoiceproto=true (see settings.gradle.kts):
    // the release APK must not contain any of it, and a normal debug build is the same as before F7.
    if (findProject(":voiceproto") != null) debugImplementation(project(":voiceproto"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}


room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
}
