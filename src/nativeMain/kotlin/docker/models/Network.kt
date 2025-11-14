package docker.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Docker network.
 */
@Serializable
data class Network(
    @SerialName("Name") val name: String,
    @SerialName("Id") val id: String,
    @SerialName("Created") val created: String? = null,
    @SerialName("Scope") val scope: String,
    @SerialName("Driver") val driver: String,
    @SerialName("EnableIPv6") val enableIPv6: Boolean? = null,
    @SerialName("Internal") val internal: Boolean? = null,
    @SerialName("Attachable") val attachable: Boolean? = null,
    @SerialName("IPAM") val ipam: IPAM? = null,
    @SerialName("Containers") val containers: Map<String, NetworkContainer>? = null,
    @SerialName("Options") val options: Map<String, String>? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null
)

/**
 * IPAM (IP Address Management) configuration for a network.
 */
@Serializable
data class IPAM(
    @SerialName("Driver") val driver: String = "default",
    @SerialName("Config") val config: List<IPAMConfigEntry>? = null,
    @SerialName("Options") val options: Map<String, String>? = null
)

/**
 * IPAM configuration entry.
 */
@Serializable
data class IPAMConfigEntry(
    @SerialName("Subnet") val subnet: String? = null,
    @SerialName("IPRange") val ipRange: String? = null,
    @SerialName("Gateway") val gateway: String? = null,
    @SerialName("AuxiliaryAddresses") val auxiliaryAddresses: Map<String, String>? = null
)

/**
 * Container information within a network.
 */
@Serializable
data class NetworkContainer(
    @SerialName("Name") val name: String? = null,
    @SerialName("EndpointID") val endpointId: String? = null,
    @SerialName("MacAddress") val macAddress: String? = null,
    @SerialName("IPv4Address") val ipv4Address: String? = null,
    @SerialName("IPv6Address") val ipv6Address: String? = null
)

/**
 * Configuration for creating a network.
 */
@Serializable
data class NetworkCreateRequest(
    @SerialName("Name") val name: String,
    @SerialName("Driver") val driver: String = "bridge",
    @SerialName("Internal") val internal: Boolean? = null,
    @SerialName("Attachable") val attachable: Boolean? = null,
    @SerialName("EnableIPv6") val enableIPv6: Boolean? = null,
    @SerialName("IPAM") val ipam: IPAM? = null,
    @SerialName("Options") val options: Map<String, String>? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null
)

/**
 * Response from network creation.
 */
@Serializable
data class NetworkCreateResponse(
    @SerialName("Id") val id: String,
    @SerialName("Warning") val warning: String? = null
)

/**
 * Configuration for connecting a container to a network.
 */
@Serializable
data class NetworkConnectRequest(
    @SerialName("Container") val container: String,
    @SerialName("EndpointConfig") val endpointConfig: EndpointConfig? = null
)

/**
 * Configuration for disconnecting a container from a network.
 */
@Serializable
data class NetworkDisconnectRequest(
    @SerialName("Container") val container: String,
    @SerialName("Force") val force: Boolean? = null
)

/**
 * Response from network prune operation.
 */
@Serializable
data class NetworkPruneResponse(
    @SerialName("NetworksDeleted") val networksDeleted: List<String>? = null
)
