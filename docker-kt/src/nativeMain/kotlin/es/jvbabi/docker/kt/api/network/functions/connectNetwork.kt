package es.jvbabi.docker.kt.api.network.functions

import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ConnectNetworkRequest(
    @SerialName("Container") val container: String,
    @SerialName("EndpointConfig") val endpointConfig: EndpointConfig
) {
    @Serializable
    data class EndpointConfig(
        @SerialName("Aliases") val aliases: List<String> = emptyList()
    )
}

@Serializable
private data class DisconnectNetworkRequest(
    @SerialName("Container") val container: String,
    @SerialName("Force") val force: Boolean
)

internal suspend fun internalConnectNetworkRequest(
    dockerClient: DockerClient,
    networkId: String,
    containerId: String,
    aliases: List<String>
) {
    val response = dockerClient.socket.post("/networks/$networkId/connect") {
        contentType(ContentType.Application.Json)
        setBody(
            ConnectNetworkRequest(
                container = containerId,
                endpointConfig = ConnectNetworkRequest.EndpointConfig(aliases)
            )
        )
    }
    if (response.status.isSuccess()) return

    throw RuntimeException(
        "Failed to connect container to network: ${response.status.value} ${response.bodyAsText()}"
    )
}

/**
 * @param force detaches the container even while it is running
 */
internal suspend fun internalDisconnectNetworkRequest(
    dockerClient: DockerClient,
    networkId: String,
    containerId: String,
    force: Boolean
) {
    val response = dockerClient.socket.post("/networks/$networkId/disconnect") {
        contentType(ContentType.Application.Json)
        setBody(DisconnectNetworkRequest(container = containerId, force = force))
    }
    if (response.status.isSuccess()) return

    throw RuntimeException(
        "Failed to disconnect container from network: ${response.status.value} ${response.bodyAsText()}"
    )
}
