package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.api.DockerContainer
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal suspend fun getContainers(
    dockerClient: DockerClient,
    all: Boolean = false
): List<DockerContainer> {
    val url = if (all) "/containers/json?all=true" else "/containers/json"
    val response = dockerClient.socket.get(url)
    return response.body()
}

