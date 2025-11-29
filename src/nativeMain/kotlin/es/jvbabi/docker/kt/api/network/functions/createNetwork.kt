package es.jvbabi.docker.kt.api.network.functions

import es.jvbabi.docker.kt.api.network.IpamConfig
import es.jvbabi.docker.kt.api.network.NetworkDriver
import es.jvbabi.docker.kt.api.network.NetworkScope
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.util.Optional
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateNetworkRequest(
    @SerialName("Name") val name: String,
    @SerialName("Driver") val driver: String,
    @SerialName("Scope") val scope: String,
    @SerialName("Internal") val internal: Boolean,
    @SerialName("Attachable") val attachable: Boolean,
    @SerialName("IPAM") val ipamConfig: Optional<IpamConfig>,
    @SerialName("EnableIPv6") val enableIPv6: Boolean,
    @SerialName("EnableIPv4") val enableIPv4: Boolean,
    @SerialName("Labels") val labels: Map<String, String>
) {
    @Serializable
    data class IpamConfig(
        @SerialName("Driver") val driver: String,
        @SerialName("Config") val configs: List<IpamConfig>
    ) {
        @Serializable
        data class IpamConfig(
            @SerialName("Subnet") val subnet: String,
            @SerialName("IPRange") val ipRange: String,
            @SerialName("Gateway") val gateway: String,
            @SerialName("AuxiliaryAddresses") val auxiliaryAddresses: Map<String, String> = emptyMap(),
        )
    }
}

internal suspend fun internalCreateNetworkRequest(
    dockerClient: DockerClient,
    name: String,
    driver: NetworkDriver = NetworkDriver.Bridge,
    scope: NetworkScope = NetworkScope.Local,
    ipamConfigs: List<IpamConfig>?,
    internal: Boolean,
    attachable: Boolean,
    enableIPv4: Boolean,
    enableIPv6: Boolean,
    labels: Map<String, String>
) {
    val request = CreateNetworkRequest(
        name = name,
        driver = when (driver) {
            NetworkDriver.Bridge -> "bridge"
            NetworkDriver.Overlay -> "overlay"
            NetworkDriver.Host -> "host"
        },
        scope = when (scope) {
            NetworkScope.Local -> "local"
            NetworkScope.Swarm -> "swarm"
        },
        internal = internal,
        attachable = attachable,
        ipamConfig = ipamConfigs?.let {
            Optional.Defined(CreateNetworkRequest.IpamConfig(
                driver = "default",
                configs = ipamConfigs.map {
                    CreateNetworkRequest.IpamConfig.IpamConfig(
                        subnet = it.subnet,
                        ipRange = it.ipRange,
                        gateway = it.gateway,
                        auxiliaryAddresses = it.auxAddress
                    )
                }
            ))
        } ?: Optional.Undefined,
        enableIPv4 = enableIPv4,
        enableIPv6 = enableIPv6,
        labels = labels
    )

    val url = URLBuilder().apply {
        protocol = URLProtocol.HTTP
        host = "localhost"
        pathSegments = listOf("networks", "create")
    }

    dockerClient.socket.preparePost(url.build()) {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.execute { response ->
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to create network: ${response.status.value} ${response.bodyAsText()}")
        }
    }
}