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
    namespace = "tj.app.quran_todo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tj.app.quran_todo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 7
        versionName = "1.0.2"
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
        getByName("release") {
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
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
