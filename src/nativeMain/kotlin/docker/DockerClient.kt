package docker

import api.info.DockerInfo
import io.ktor.client.call.*
import io.ktor.client.request.*

class DockerClient: AutoCloseable {
    private val httpClient = getHttpClient()

    suspend fun getInfo(): DockerInfo {
        val response = httpClient.get("/info")
        return response.body()
    }

    override fun close() {
        httpClient.close()
    }
}