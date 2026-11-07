package tj.app.quran_todo.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The shared HTTP client. [debug] comes from the platform DI: in debug builds request lines and statuses are
 * logged ([LogLevel.INFO]; never headers or bodies, a response can be a whole surah of text); in release
 * builds the client logs nothing.
 */
fun createHttpClient(engine: HttpClientEngine, debug: Boolean = false): HttpClient {
    return HttpClient(engine) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
        if (debug) {
            install(Logging) {
                level = LogLevel.INFO
            }
        }
        install(ContentNegotiation) {
            json(Json {
                explicitNulls = true
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
}
