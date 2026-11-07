rootProject.name = "QuranTodo"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
    }
}

include(":composeApp")
// F7 prototype (on-device voice check): debug builds only and only when asked for: -Pvoiceproto=true, or voiceproto=true in
// local.properties / the user's gradle.properties. Off by default, so a normal build neither compiles whisper.cpp nor downloads it.
val voiceprotoOn = (providers.gradleProperty("voiceproto").orNull
    ?: File(rootDir, "local.properties").takeIf { it.isFile }?.readLines()
        ?.firstOrNull { it.trim().startsWith("voiceproto=") }?.substringAfter("=")?.trim()) == "true"
if (voiceprotoOn) include(":voiceproto")
