package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal suspend fun killContainer(dockerClient: DockerClient, id: String) {
    val response = dockerClient.socket.post("/containers/$id/kill")
    if (response.status.isSuccess()) return

    throw RuntimeException("Failed to kill container: ${response.status.value} ${response.bodyAsText()}")
}


