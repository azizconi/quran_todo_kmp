import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * F7 prototype: whisper.cpp on the phone (JNI) and the recorder of 16 kHz PCM. A debug-only module: `composeApp` takes it with
 * `debugImplementation` only, so the release APK has neither this code nor `libvoiceproto.so`.
 *
 * whisper.cpp is not copied into the repository: CMake downloads the tagged source archive of the release (URL + SHA-256, see
 * src/androidMain/cpp/CMakeLists.txt). To build offline point `-Pwhisper.src=<an unpacked copy of the same tag>` at it.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

android {
    namespace = "tj.app.quran_todo.voiceproto"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            // The emulator of an Apple Silicon Mac and every phone of today is arm64. x86_64 is left out on purpose (build time, size).
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                val local = providers.gradleProperty("whisper.src").orNull
                arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release")
                if (local != null) arguments += "-DWHISPER_SRC_DIR=$local"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
