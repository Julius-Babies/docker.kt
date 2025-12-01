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
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("ExposedPorts") val exposedPorts: Map<String, EmptyObject> = emptyMap()
)

@Serializable
private data class HostConfig(
    @SerialName("Binds") val binds: List<String> = emptyList(),
    @SerialName("PortBindings") val portBindings: Map<String, List<PortBinding>> = emptyMap()
)

@Serializable
private data class PortBinding(
    @SerialName("HostIp") val hostIp: String = "",
    @SerialName("HostPort") val hostPort: String
)

@Serializable
private class EmptyObject

internal suspend fun createContainer(
    dockerClient: DockerClient,
    image: String,
    name: String? = null,
    volumeBinds: Map<VolumeBind, String> = emptyMap(),
    environment: Map<String, String> = emptyMap(),
    labels: Map<String, String> = emptyMap(),
    ports: Map<Int, Int> = emptyMap(),
    exposedPorts: List<Int> = emptyList()
) {
    val binds = volumeBinds.map { (bind, containerPath) ->
        val mountPath = when (bind) {
            is VolumeBind.Host -> "${bind.path}:$containerPath"
            is VolumeBind.Volume -> "${bind.name}:$containerPath"
        }
        if (bind.readOnly) "$mountPath:ro" else mountPath
    }

    val envList = environment.map { (k, v) -> "$k=$v" }

    // Build port bindings: Map<String, List<PortBinding>>
    val portBindings: Map<String, List<PortBinding>> = ports.mapKeys { (containerPort, _) ->
        "$containerPort/tcp"
    }.mapValues { (_, hostPort) ->
        listOf(PortBinding(hostPort = hostPort.toString()))
    }

    // Build exposed ports from both port mappings and explicitly exposed ports
    val allExposedPorts: Map<String, EmptyObject> =
        (ports.keys + exposedPorts).distinct().associate { port ->
            "$port/tcp" to EmptyObject()
        }

    val request = CreateContainerRequest(
        image = image,
        hostConfig = HostConfig(
            binds = binds,
            portBindings = portBindings
        ),
        env = envList,
        labels = labels,
        exposedPorts = allExposedPorts
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