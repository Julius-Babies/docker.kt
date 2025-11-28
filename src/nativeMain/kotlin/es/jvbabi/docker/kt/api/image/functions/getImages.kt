package es.jvbabi.docker.kt.api.image.functions

import es.jvbabi.docker.kt.api.image.api.DockerImage
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal suspend fun getImages(dockerClient: DockerClient): List<DockerImage> {
    val response = dockerClient.socket.get("/images/json")
    return response.body()
}