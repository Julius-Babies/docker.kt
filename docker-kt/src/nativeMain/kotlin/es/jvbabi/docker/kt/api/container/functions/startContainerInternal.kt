package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.ContainerAlreadyRunningException
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

internal suspend fun startContainerInternal(
    dockerClient: DockerClient,
    id: String,
    exceptionOnAlreadyRunning: Boolean,
) {
    val response = try {
        dockerClient.socket.post("/containers/$id/start")
    } catch (e: RedirectResponseException) {
        if ("not modified" in e.message.lowercase()) {
            if (!exceptionOnAlreadyRunning) return
            throw ContainerAlreadyRunningException(id)
        }
        else throw e
    }
    if (response.status.isSuccess()) return

    throw RuntimeException("Failed to start container: ${response.status.value} ${response.bodyAsText()}")
}

