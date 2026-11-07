package tj.app.quran_todo.common.audio

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The plain HTTP download of one recording, against a real server on this machine. */
class HttpAudioFetchTest {
    private lateinit var dir: File
    private lateinit var server: HttpServer
    private val remotePorts: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("http-audio-fetch-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            remotePorts += exchange.remoteAddress.port
            reply(exchange, 200, ByteArray(20_000) { 3 })
        }
        server.createContext("/missing") { exchange -> reply(exchange, 404, ByteArray(10)) }
        server.createContext("/cut") { exchange ->
            // Says 5000 bytes, sends 1000 and closes the connection.
            exchange.sendResponseHeaders(200, 5_000)
            exchange.responseBody.write(ByteArray(1_000))
            exchange.responseBody.flush()
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun reply(exchange: HttpExchange, code: Int, body: ByteArray) {
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun aWholeFileIsWrittenToTheTarget() = runTest {
        val target = File(dir, "a.mp3.download")
        assertTrue(httpAudioFetch(url("/ok"), target))
        assertEquals(20_000L, target.length())
    }

    @Test
    fun anErrorAnswerIsNotAFile() = runTest {
        assertFalse(httpAudioFetch(url("/missing"), File(dir, "b.mp3.download")))
    }

    @Test
    fun aBodyThatStopsEarlyIsNotAFile() = runTest {
        assertFalse(httpAudioFetch(url("/cut"), File(dir, "c.mp3.download")))
    }

    @Test
    fun anUnreachableServerIsNotAFileAndDoesNotThrow() = runTest {
        server.stop(0)
        assertFalse(httpAudioFetch(url("/ok"), File(dir, "d.mp3.download")))
    }

    @Test
    fun filesOneAfterAnotherReuseTheSameConnection() = runTest {
        // Closing the stream of a finished body hands the connection back to the pool; disconnect() would have closed it, and every
        // file would have paid a new TCP (and, on the real CDN, TLS) handshake: seconds per file on a slow network.
        repeat(4) { n -> assertTrue(httpAudioFetch(url("/ok"), File(dir, "e$n.mp3.download"))) }
        assertEquals(1, remotePorts.size, "the client connected from ports $remotePorts")
    }
}
