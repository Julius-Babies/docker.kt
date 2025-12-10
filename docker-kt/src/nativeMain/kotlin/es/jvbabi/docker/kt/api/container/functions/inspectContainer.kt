package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.api.Inspect
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal suspend fun inspectContainer(
    dockerClient: DockerClient,
    containerId: String
): Inspect {
    val url = "/containers/$containerId/json"
    val response = dockerClient.socket.get(url)
    return response.body()
}