# R8 rules of the release build (composeApp/build.gradle.kts: isMinifyEnabled + isShrinkResources).
# Most libraries ship their own consumer rules (Room, kotlinx.serialization, Ktor's OkHttp engine, Koin,
# Firebase, Media3, AndroidX); only what R8 reports on top of them is listed here, each with the reason.

# Readable crash reports: Crashlytics needs file names and line numbers; the plugin uploads mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation routes (ui/navigation/Dest.kt) are @Serializable and found through their companions/serializers.
# R8 printed no warning for them: the kotlinx.serialization consumer rules already keep them (checked in
# build/outputs/mapping/release/seeds.txt). Kept explicitly as well, so that a change of those rules
# cannot turn every navigate() call into a crash that only a release build shows.
-keep class tj.app.quran_todo.ui.navigation.Dest { *; }
-keep class tj.app.quran_todo.ui.navigation.Dest$* { *; }

# Exception class names go into the anonymous "non_fatal_error" analytics event (AppTelemetry): keep them readable.
-keepnames class * extends java.lang.Throwable
