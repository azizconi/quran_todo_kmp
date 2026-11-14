package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.ios.File
import kotlinx.serialization.json.Json

/** The queue as one JSON file, written to a temporary file and renamed so a half-written file is never read. */
internal class FileQueueStore(private val file: File) : QueueStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): QueueState = try {
        if (file.exists()) json.decodeFromString(QueueState.serializer(), file.readText()) else QueueState()
    } catch (_: Exception) {
        // A damaged file is the same as no queue: the files already downloaded are still there.
        QueueState()
    }

    override fun save(state: QueueState) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.encodeToString(QueueState.serializer(), state))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }
}
