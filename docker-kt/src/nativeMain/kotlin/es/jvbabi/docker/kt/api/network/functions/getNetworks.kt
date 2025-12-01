package es.jvbabi.docker.kt.api.network.functions

import es.jvbabi.docker.kt.api.network.NetworkDriver
import es.jvbabi.docker.kt.api.network.NetworkScope
import es.jvbabi.docker.kt.api.network.api.Network
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DockerNetworksResponse(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Scope") val scope: String,
    @SerialName("Driver") val driver: String,
    @SerialName("EnableIPv6") val enableIPv6: Boolean,
    @SerialName("EnableIPv4") val enableIPv4: Boolean,
    @SerialName("Internal") val internal: Boolean,
    @SerialName("Attachable") val attachable: Boolean,
    @SerialName("IPAM") val ipam: Ipam,
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("Created") val createdAt: String
) {
    @Serializable
    data class Ipam(
        @SerialName("Driver") val driver: String,
        @SerialName("Config") val configs: List<IpamConfig>?
    ) {
        @Serializable
        data class IpamConfig(
            @SerialName("Subnet") val subnet: String,
            @SerialName("Gateway") val gateway: String,
            @SerialName("AuxiliaryAddresses") val auxiliaryAddresses: Map<String, String> = emptyMap(),
            @SerialName("IPRange") val ipRange: String? = null
        )
    }
}

suspend fun internalGetNetworksRequest(dockerClient: DockerClient): List<Network> {
    val url = URLBuilder().apply {
        protocol = URLProtocol.HTTP
        host = "localhost"
        pathSegments = listOf("networks")
    }

    val response = dockerClient.socket.get(url.build())
    if (!response.status.isSuccess()) throw RuntimeException("Failed to get networks: ${response.status.value} ${response.bodyAsText()}")
    val data = response.body<List<DockerNetworksResponse>>()
    return data.map { network ->
        Network(
            id = network.id,
            name = network.name,
            scope = when (network.scope) {
                "swarm" -> NetworkScope.Swarm
                "local" -> NetworkScope.Local
                else -> throw RuntimeException("Unknown network scope: ${network.scope}")
            },
            driver = when (network.driver) {
                "bridge" -> NetworkDriver.Bridge
                "overlay" -> NetworkDriver.Overlay
                "host" -> NetworkDriver.Host
                "null" -> NetworkDriver.Null
                else -> throw RuntimeException("Unknown network driver: ${network.driver}")
            },
            enableIPv4 = network.enableIPv4,
            enableIPv6 = network.enableIPv6,
            internal = network.internal,
            attachable = network.attachable,
            ipam = Network.Ipam(
                driver = network.ipam.driver,
                configs = network.ipam.configs?.map { config ->
                    Network.Ipam.IpamConfig(
                        subnet = config.subnet,
                        ipRange = config.ipRange,
                        gateway = config.gateway,
                        auxAddress = config.auxiliaryAddresses
                    )
                }
            ),
            labels = network.labels,
            created = Instant.parse(network.createdAt)
        )
    }
}
