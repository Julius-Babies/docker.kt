package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal suspend fun deleteContainer(dockerClient: DockerClient, id: String) {
    val response = dockerClient.socket.delete("/containers/$id")
    if (response.status.isSuccess()) return

    throw RuntimeException("Failed to delete container: ${response.status.value} ${response.bodyAsText()}")
}


