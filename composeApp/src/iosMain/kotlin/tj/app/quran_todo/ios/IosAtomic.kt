package tj.app.quran_todo.ios

import kotlin.concurrent.AtomicInt

/** The `java.util.concurrent.atomic.AtomicInteger` of the ported runners, over the atomic of Kotlin/Native. */
internal class AtomicInteger(initial: Int) {
    private val value = AtomicInt(initial)

    fun get(): Int = value.value

    fun set(next: Int) {
        value.value = next
    }

    fun incrementAndGet(): Int = value.incrementAndGet()
}
