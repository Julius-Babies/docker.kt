package es.jvbabi.docker.kt.docker


import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

actual fun getHttpClient() = HttpClient(CIO) {
    expectSuccess = true

    defaultRequest {
        unixSocket(getSocketPath())
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = false
            isLenient = true
            prettyPrint = true
        })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 24.hours.inWholeMilliseconds
        connectTimeoutMillis = 15.seconds.inWholeMilliseconds
        socketTimeoutMillis = 24.hours.inWholeMilliseconds
    }
}
