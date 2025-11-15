@file:OptIn(ExperimentalForeignApi::class)

package docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getenv
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

fun getSocketPath(): String {
    val systemPath = "/var/run/docker.sock"

    if (access(systemPath, F_OK) == 0) return systemPath

    val userPath = getenv("HOME")?.toKString()?.plus("/.docker/run/docker.sock") ?: throw IllegalStateException(
        "Could not find Docker socket. Tried $systemPath."
    )

    if (access(userPath, F_OK) == 0) return userPath

    throw IllegalStateException("Could not find Docker socket. Tried $systemPath and $userPath.")
}

fun getHttpClient(): HttpClient {
    val httpClient = HttpClient(CIO) {
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

    return httpClient
}