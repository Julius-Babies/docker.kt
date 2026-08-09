package es.jvbabi.docker.kt.api.network.functions

import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
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

/**
 * Turns what the daemon reports into a [Network] that can be worked with straight away: id filled
 * in, state [Network.State.Created].
 */
internal fun DockerNetworksResponse.toNetwork(client: DockerClient): Network = Network(
    client = client,
    name = name,
    scope = when (scope) {
        "swarm" -> Network.Scope.Swarm
        "local" -> Network.Scope.Local
        else -> throw RuntimeException("Unknown network scope: $scope")
    },
    driver = when (driver) {
        "bridge" -> Network.Driver.Bridge
        "overlay" -> Network.Driver.Overlay
        "host" -> Network.Driver.Host
        "null" -> Network.Driver.Null
        else -> throw RuntimeException("Unknown network driver: $driver")
    },
    enableIpv4 = enableIPv4,
    enableIpv6 = enableIPv6,
    internal = internal,
    attachable = attachable,
    ipamConfigs = ipam.configs.orEmpty().map { config ->
        Network.IpamConfig(
            subnet = config.subnet,
            ipRange = config.ipRange,
            gateway = config.gateway,
            auxAddress = config.auxiliaryAddresses
        )
    },
    labels = labels
).apply {
    this.id = this@toNetwork.id
    created = Instant.parse(createdAt)
    state = Network.State.Created
}

suspend fun internalGetNetworksRequest(dockerClient: DockerClient): List<Network> {
    val url = URLBuilder().apply {
        protocol = URLProtocol.HTTP
        host = "localhost"
        pathSegments = listOf("networks")
    }

    val response = dockerClient.socket.get(url.build())
    if (!response.status.isSuccess()) throw RuntimeException("Failed to get networks: ${response.status.value} ${response.bodyAsText()}")
    return response.body<List<DockerNetworksResponse>>().map { it.toNetwork(dockerClient) }
}

/**
 * @return the network with that id, or null if the daemon does not know it
 */
internal suspend fun internalGetNetworkByIdRequest(dockerClient: DockerClient, id: String): Network? {
    val url = URLBuilder().apply {
        protocol = URLProtocol.HTTP
        host = "localhost"
        pathSegments = listOf("networks", id)
    }

    val response = try {
        dockerClient.socket.get(url.build())
    } catch (e: ClientRequestException) {
        // The client runs with expectSuccess, so Docker's 404 arrives as an exception - but an id
        // that does not exist is an answer here, not a failure.
        if (e.response.status == HttpStatusCode.NotFound) return null
        throw e
    }

    return response.body<DockerNetworksResponse>().toNetwork(dockerClient)
}
