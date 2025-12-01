package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal suspend fun pauseContainer(dockerClient: DockerClient, id: String) {
    val response = dockerClient.socket.post("/containers/$id/pause")
    if (response.status.isSuccess()) return

    throw RuntimeException("Failed to pause container: ${response.status.value} ${response.bodyAsText()}")
}

