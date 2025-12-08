package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.NetworkConfig
import es.jvbabi.docker.kt.api.container.VolumeBind
import es.jvbabi.docker.kt.api.image.ImageNotFoundException
import es.jvbabi.docker.kt.docker.DockerClient
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
    @SerialName("Entrypoint") val entrypoint: List<String>? = null,
    @SerialName("Cmd") val cmd: List<String>? = null,
    @SerialName("ExposedPorts") val exposedPorts: Map<String, EmptyObject> = emptyMap(),
    @SerialName("NetworkingConfig") val networkingConfig: NetworkingConfig
) {
    @Serializable
    data class NetworkingConfig(
        @SerialName("EndpointsConfig") val endpointsConfig: Map<String, EndpointConfig>
    ) {
        @Serializable
        data class EndpointConfig(
            @SerialName("Aliases") val aliases: List<String> = emptyList()
        )
    }
}

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

internal suspend fun createContainerInternal(
    dockerClient: DockerClient,
    image: String,
    name: String?,
    volumeBinds: Map<VolumeBind, String>,
    environment: Map<String, String>,
    labels: Map<String, String>,
    ports: List<es.jvbabi.docker.kt.api.container.PortBinding>,
    exposedPorts: Map<Int, es.jvbabi.docker.kt.api.container.PortBinding.Protocol>,
    networkConfigs: List<NetworkConfig>,
    cmd: List<String>?,
    entrypoint: List<String>?
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
    val portBindings: Map<String, List<PortBinding>> = ports.associate { binding ->
        "${binding.containerPort}/${binding.protocol.name.lowercase()}" to listOf(PortBinding(hostPort = binding.hostPort.toString()))
    }

    // Build exposed ports from both port mappings and explicitly exposed ports
    val allExposedPorts: Map<String, EmptyObject> = ports
        .map { "${it.containerPort}/${it.protocol.name.lowercase()}" }
        .plus(exposedPorts.map { "${it.key}/${it.value.name.lowercase()}" })
        .associateWith { EmptyObject() }

    val request = CreateContainerRequest(
        image = image,
        hostConfig = HostConfig(
            binds = binds,
            portBindings = portBindings
        ),
        env = envList,
        entrypoint = entrypoint,
        cmd = cmd,
        labels = labels,
        exposedPorts = allExposedPorts,
        networkingConfig = CreateContainerRequest.NetworkingConfig(networkConfigs.associate { networkConfig ->
            networkConfig.networkId to CreateContainerRequest.NetworkingConfig.EndpointConfig(
                aliases = networkConfig.aliases
            )
        })
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