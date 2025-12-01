package es.jvbabi.docker.kt.docker

import es.jvbabi.docker.kt.api.image.ImageApi
import es.jvbabi.docker.kt.api.info.DockerInfo
import es.jvbabi.docker.kt.api.network.NetworkApi
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

expect fun getHttpClient(): HttpClient

class DockerClient: AutoCloseable {
    internal val socket = getHttpClient()
    internal val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    val images = ImageApi(this)
    val networks = NetworkApi(this)

    suspend fun getInfo(): DockerInfo {
        val response = socket.get("/info")
        return response.body()
    }

    suspend fun ping(): Boolean {
        val response = socket.get("/_ping")
        return response.status.isSuccess()
    }

    override fun close() {
        socket.close()
        httpClient.close()
    }
}