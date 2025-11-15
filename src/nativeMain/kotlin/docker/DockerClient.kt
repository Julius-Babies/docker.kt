package docker

import api.image.ImageApi
import api.info.DockerInfo
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class DockerClient: AutoCloseable {
    internal val httpClient = getHttpClient()

    val images = ImageApi(this)

    suspend fun getInfo(): DockerInfo {
        val response = httpClient.get("/info")
        println(response.bodyAsText())
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