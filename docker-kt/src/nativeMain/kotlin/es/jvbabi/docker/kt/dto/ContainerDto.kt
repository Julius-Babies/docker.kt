/*
 * Wire types: how the Docker daemon represents things on the socket, one to one.
 *
 * Everything in this package is internal on purpose - it exists so the request functions have
 * something to deserialise into, and it changes whenever Docker's API does. The library hands out
 * its own types instead, which the api package builds from these.
 */
package es.jvbabi.docker.kt.dto

import es.jvbabi.docker.kt.api.container.ContainerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DockerContainer(
    @SerialName("Id")
    val id: String,

    @SerialName("Names")
    val names: List<String>,

    @SerialName("Image")
    val image: String,

    @SerialName("ImageID")
    val imageId: String,

    @SerialName("Command")
    val command: String,

    @SerialName("Created")
    val created: Long,

    @SerialName("State")
    private val stateString: String,

    @SerialName("Status")
    val status: String,

    @SerialName("Ports")
    val ports: List<Port>? = emptyList(),

    @SerialName("Labels")
    val labels: Map<String, String> = emptyMap(),

    @SerialName("SizeRw")
    val sizeRw: Long? = null,

    @SerialName("SizeRootFs")
    val sizeRootFs: Long? = null,

    @SerialName("Mounts")
    val mounts: List<Mount> = emptyList(),

    @SerialName("NetworkSettings")
    val networkSettings: NetworkSettings? = null
) {
    val state: ContainerState
        get() = ContainerState.fromString(stateString) ?: ContainerState.EXITED
}

@Serializable
internal data class Port(
    @SerialName("IP")
    val ip: String? = null,

    @SerialName("PrivatePort")
    val privatePort: Int,

    @SerialName("PublicPort")
    val publicPort: Int? = null,

    @SerialName("Type")
    val type: String
)

@Serializable
internal data class Mount(
    @SerialName("Type")
    val type: String,

    @SerialName("Name")
    val name: String? = null,

    @SerialName("Source")
    val source: String,

    @SerialName("Destination")
    val destination: String,

    @SerialName("Driver")
    val driver: String? = null,

    @SerialName("Mode")
    val mode: String,

    @SerialName("RW")
    val rw: Boolean,

    @SerialName("Propagation")
    val propagation: String
)

@Serializable
internal data class NetworkSettings(
    @SerialName("Networks")
    val networks: Map<String, Network> = emptyMap()
)

@Serializable
internal data class Network(
    @SerialName("NetworkID")
    val networkId: String,

    @SerialName("EndpointID")
    val endpointId: String,

    @SerialName("Gateway")
    val gateway: String,

    @SerialName("IPAddress")
    val ipAddress: String,

    @SerialName("IPPrefixLen")
    val ipPrefixLen: Int,

    @SerialName("IPv6Gateway")
    val ipv6Gateway: String,

    @SerialName("GlobalIPv6Address")
    val globalIpv6Address: String,

    @SerialName("GlobalIPv6PrefixLen")
    val globalIpv6PrefixLen: Int,

    @SerialName("MacAddress")
    val macAddress: String
)

