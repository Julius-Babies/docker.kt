package es.jvbabi.docker.kt.api.network.functions

import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

suspend fun internalDeleteNetworkRequest(dockerClient: DockerClient, id: String) {
    val response = dockerClient.socket.delete("/networks/$id")
    if (response.status.isSuccess()) return

    throw RuntimeException("Failed to delete network: ${response.status.value} ${response.bodyAsText()}")
}