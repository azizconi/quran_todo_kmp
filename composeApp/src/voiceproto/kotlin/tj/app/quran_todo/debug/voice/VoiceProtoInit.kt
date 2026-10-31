package tj.app.quran_todo.debug.voice

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import tj.app.quran_todo.ui.hifz.VoiceProtoHook

/**
 * Debug builds only (F7 prototype): a provider without data whose `onCreate` runs when the process starts, before any screen. It hangs
 * the "Check by voice" panel on [VoiceProtoHook], so the main code needs no reference to the debug classes. Declared in
 * `src/debug/AndroidManifest.xml`, which a release build does not read.
 */
class VoiceProtoInit : ContentProvider() {
    override fun onCreate(): Boolean {
        VoiceProtoHook.passageEntry = { ayahs -> VoiceCheckPanel(ayahs) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
