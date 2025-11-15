package api.image

import docker.DockerClient
import io.ktor.client.call.*
import io.ktor.client.request.*

class ImageApi internal constructor(private val client: DockerClient) {
    suspend fun getImages(): List<DockerImage> {
        val response = client.httpClient.get("/images/json")
        return response.body()
    }
}