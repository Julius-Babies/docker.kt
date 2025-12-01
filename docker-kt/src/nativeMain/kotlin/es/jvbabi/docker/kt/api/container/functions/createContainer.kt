package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.VolumeBind
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.api.image.ImageNotFoundException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CreateContainerRequest(
    @SerialName("Image") val image: String,
    @SerialName("HostConfig") val hostConfig: HostConfig = HostConfig(),
    @SerialName("Env") val env: List<String> = emptyList(),
    @SerialName("Labels") val labels: Map<String, String> = emptyMap()
) {
    @Serializable
    data class HostConfig(
        @SerialName("Binds") val binds: List<String> = emptyList()
    )
}

internal suspend fun createContainer(
    dockerClient: DockerClient,
    image: String,
    name: String? = null,
    volumeBinds: Map<VolumeBind, String> = emptyMap(),
    environment: Map<String, String> = emptyMap(),
    labels: Map<String, String> = emptyMap()
) {
    val binds = volumeBinds.map { (bind, containerPath) ->
        when (bind) {
            is VolumeBind.Host -> "${bind.path}:$containerPath"
            is VolumeBind.Volume -> "${bind.name}:$containerPath"
        }
    }

    val envList = environment.map { (k, v) -> "$k=$v" }

    val request = CreateContainerRequest(
        image = image,
        hostConfig = CreateContainerRequest.HostConfig(binds = binds),
        env = envList,
        labels = labels
    )

    val url = URLBuilder().apply {
        protocol = URLProtocol.HTTP
        host = "localhost"
        pathSegments = listOf("containers", "create")
        if (name != null) parameters.append("name", name)
    }

    dockerClient.socket.preparePost(url.build()) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.execute { response ->
        if (response.status == HttpStatusCode.NotFound) {
            // Docker returns 404 if the image does not exist
            throw ImageNotFoundException(image)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to create container: ${response.status.value} ${response.bodyAsText()}")
        }
    }
}