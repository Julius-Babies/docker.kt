package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.api.Inspect
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

internal suspend fun inspectContainer(
    dockerClient: DockerClient,
    containerId: String
): Inspect {
    val url = "/containers/$containerId/json"
    val response = dockerClient.socket.get(url)
    return response.body()
}

/**
 * @return the container's details, or null if the daemon does not know that id
 */
internal suspend fun inspectContainerOrNull(
    dockerClient: DockerClient,
    containerId: String
): Inspect? = try {
    inspectContainer(dockerClient, containerId)
} catch (e: ClientRequestException) {
    // The client runs with expectSuccess, so Docker's 404 arrives as an exception - but an id that
    // does not exist is an answer here, not a failure.
    if (e.response.status == HttpStatusCode.NotFound) null else throw e
}