package docker

import api.info.DockerInfo
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess

class DockerClient: AutoCloseable {
    private val httpClient = getHttpClient()

    suspend fun getInfo(): DockerInfo {
        val response = httpClient.get("/info")
        return response.body()
    }

    suspend fun ping(): Boolean {
        val response = httpClient.get("/_ping")
        return response.status.isSuccess()
    }

    override fun close() {
        httpClient.close()
    }
}