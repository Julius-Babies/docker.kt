package es.jvbabi.docker.kt.docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
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
            ignoreUnknownKeys = true
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