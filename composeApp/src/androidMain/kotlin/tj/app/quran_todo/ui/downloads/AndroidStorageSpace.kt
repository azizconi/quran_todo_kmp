package tj.app.quran_todo.ui.downloads

import android.os.StatFs
import tj.app.quran_todo.common.platform.AndroidContextHolder

/** Bytes free on the storage the downloads are written to. */
internal object AndroidStorageSpace : StorageSpace {
    override fun freeBytes(): Long = try {
        StatFs(AndroidContextHolder.context.noBackupFilesDir.path).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}
