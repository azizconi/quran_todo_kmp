package tj.app.quran_todo.common.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StartupHelpersTest {
    @Test
    fun onceResolvedWorksTheValueOutOnceAndPeekNeverDoes() {
        var calls = 0
        val value = OnceResolved { ++calls }
        assertNull(value.peek())
        assertFalse(value.isReady)
        assertEquals(1, value.get())
        assertEquals(1, value.get())
        assertEquals(1, value.peek())
        assertTrue(value.isReady)
        assertEquals(1, calls)
    }

    @Test
    fun resolveQuietlySwallowsAFailureAndGetTriesAgain() {
        var calls = 0
        val value = OnceResolved {
            calls++
            if (calls == 1) error("disk") else 7
        }
        value.resolveQuietly()
        assertFalse(value.isReady)
        assertEquals(7, value.get())
        assertEquals(2, calls)
    }

    @Test
    fun awaitReturnsWhatAnotherThreadResolved() = runTest {
        val value = OnceResolved { "gate" }
        Thread { value.resolveQuietly() }.apply { start() }.join()
        assertEquals("gate", value.await())
    }

    @Test
    fun pendingCallsWaitUntilOpenedThenRunInOrderAndAtOnce() {
        val log = mutableListOf<String>()
        val calls = PendingCalls()
        calls.submit { log += "a" }
        calls.submit { log += "b" }
        assertTrue(log.isEmpty())
        calls.open()
        assertEquals(listOf("a", "b"), log)
        calls.submit { log += "c" }
        assertEquals(listOf("a", "b", "c"), log)
    }

    @Test
    fun pendingCallsKeepAtMostTheLimitAndSurviveAFailingCall() {
        val log = mutableListOf<Int>()
        val calls = PendingCalls(limit = 2)
        calls.submit { error("boom") }
        calls.submit { log += 1 }
        calls.submit { log += 2 }
        calls.open()
        assertEquals(listOf(1), log)
    }
}
