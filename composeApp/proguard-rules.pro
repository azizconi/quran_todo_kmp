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

# The backup file (hifz/backup/BackupFormat.kt): its @Serializable classes are written and read with kotlinx.serialization, whose
# consumer rules keep their serializers. Kept explicitly as well (members and the generated $$serializer classes), so that a
# change of those rules cannot turn every import into "the file is damaged" in a release build only.
-keep @kotlinx.serialization.Serializable class tj.app.quran_todo.hifz.backup.** { *; }
-keep class tj.app.quran_todo.hifz.backup.**$$serializer { *; }

# "Downloads and storage" (ui/downloads). The queue file is JSON written and read with kotlinx.serialization: kept explicitly as well,
# like the backup classes above, so that R8 cannot turn a restart in the middle of a download into an empty queue in a release build only.
-keep @kotlinx.serialization.Serializable class tj.app.quran_todo.ui.downloads.** { *; }
-keep class tj.app.quran_todo.ui.downloads.**$$serializer { *; }
# WorkManager creates the worker by reflection with this constructor (the library's consumer rules keep it too: explicit on purpose,
# a shrunk worker would silently stop every download in the background). The receiver is in the manifest, which R8 keeps.
-keep class tj.app.quran_todo.ui.downloads.DownloadWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# The tajweed markup (common/tajweed/TajweedFormat.kt): the answer of api.alquran.cloud is read with kotlinx.serialization into small
# @Serializable classes. Kept explicitly as well, like the backup classes above, so that R8 cannot turn the first download into
# "An internet connection is needed once" for ever in a release build only.
-keep @kotlinx.serialization.Serializable class tj.app.quran_todo.common.tajweed.** { *; }
-keep class tj.app.quran_todo.common.tajweed.**$$serializer { *; }
