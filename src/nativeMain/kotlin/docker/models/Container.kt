package docker.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Docker container in list operations.
 */
@Serializable
data class Container(
    @SerialName("Id") val id: String,
    @SerialName("Names") val names: List<String>,
    @SerialName("Image") val image: String,
    @SerialName("ImageID") val imageId: String,
    @SerialName("Command") val command: String,
    @SerialName("Created") val created: Long,
    @SerialName("State") val state: String,
    @SerialName("Status") val status: String,
    @SerialName("Ports") val ports: List<Port>? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null,
    @SerialName("Mounts") val mounts: List<Mount>? = null
)

/**
 * Port mapping for a container.
 */
@Serializable
data class Port(
    @SerialName("IP") val ip: String? = null,
    @SerialName("PrivatePort") val privatePort: Int,
    @SerialName("PublicPort") val publicPort: Int? = null,
    @SerialName("Type") val type: String
)

/**
 * Mount information for a container.
 */
@Serializable
data class Mount(
    @SerialName("Type") val type: String,
    @SerialName("Source") val source: String,
    @SerialName("Destination") val destination: String,
    @SerialName("Mode") val mode: String? = null,
    @SerialName("RW") val rw: Boolean? = null
)

/**
 * Configuration for creating a container.
 */
@Serializable
data class ContainerConfig(
    @SerialName("Image") val image: String,
    @SerialName("Cmd") val cmd: List<String>? = null,
    @SerialName("Entrypoint") val entrypoint: List<String>? = null,
    @SerialName("Env") val env: List<String>? = null,
    @SerialName("WorkingDir") val workingDir: String? = null,
    @SerialName("ExposedPorts") val exposedPorts: Map<String, Map<String, String>>? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null,
    @SerialName("HostConfig") val hostConfig: HostConfig? = null,
    @SerialName("NetworkingConfig") val networkingConfig: NetworkingConfig? = null
)

/**
 * Host configuration for a container.
 */
@Serializable
data class HostConfig(
    @SerialName("Binds") val binds: List<String>? = null,
    @SerialName("PortBindings") val portBindings: Map<String, List<PortBinding>>? = null,
    @SerialName("RestartPolicy") val restartPolicy: RestartPolicy? = null,
    @SerialName("AutoRemove") val autoRemove: Boolean? = null,
    @SerialName("NetworkMode") val networkMode: String? = null,
    @SerialName("Privileged") val privileged: Boolean? = null,
    @SerialName("Memory") val memory: Long? = null,
    @SerialName("MemorySwap") val memorySwap: Long? = null,
    @SerialName("CpuShares") val cpuShares: Int? = null
)

/**
 * Port binding configuration.
 */
@Serializable
data class PortBinding(
    @SerialName("HostIp") val hostIp: String? = null,
    @SerialName("HostPort") val hostPort: String
)

/**
 * Restart policy for a container.
 */
@Serializable
data class RestartPolicy(
    @SerialName("Name") val name: String = "no",
    @SerialName("MaximumRetryCount") val maximumRetryCount: Int = 0
)

/**
 * Networking configuration for a container.
 */
@Serializable
data class NetworkingConfig(
    @SerialName("EndpointsConfig") val endpointsConfig: Map<String, EndpointConfig>? = null
)

/**
 * Endpoint configuration for a network.
 */
@Serializable
data class EndpointConfig(
    @SerialName("IPAMConfig") val ipamConfig: IPAMConfig? = null,
    @SerialName("Aliases") val aliases: List<String>? = null
)

/**
 * IPAM configuration.
 */
@Serializable
data class IPAMConfig(
    @SerialName("IPv4Address") val ipv4Address: String? = null,
    @SerialName("IPv6Address") val ipv6Address: String? = null
)

/**
 * Response from container creation.
 */
@Serializable
data class ContainerCreateResponse(
    @SerialName("Id") val id: String,
    @SerialName("Warnings") val warnings: List<String>? = null
)

/**
 * Detailed information about a container.
 */
@Serializable
data class ContainerInspect(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Image") val image: String,
    @SerialName("Config") val config: ContainerConfig? = null,
    @SerialName("State") val state: ContainerState? = null,
    @SerialName("Created") val created: String,
    @SerialName("HostConfig") val hostConfig: HostConfig? = null,
    @SerialName("NetworkSettings") val networkSettings: NetworkSettings? = null
)

/**
 * State of a container.
 */
@Serializable
data class ContainerState(
    @SerialName("Status") val status: String,
    @SerialName("Running") val running: Boolean,
    @SerialName("Paused") val paused: Boolean,
    @SerialName("Restarting") val restarting: Boolean,
    @SerialName("Pid") val pid: Int,
    @SerialName("ExitCode") val exitCode: Int,
    @SerialName("StartedAt") val startedAt: String,
    @SerialName("FinishedAt") val finishedAt: String
)

/**
 * Network settings for a container.
 */
@Serializable
data class NetworkSettings(
    @SerialName("Networks") val networks: Map<String, EndpointSettings>? = null,
    @SerialName("IPAddress") val ipAddress: String? = null,
    @SerialName("Gateway") val gateway: String? = null,
    @SerialName("Ports") val ports: Map<String, List<PortBinding>>? = null
)

/**
 * Endpoint settings for a network.
 */
@Serializable
data class EndpointSettings(
    @SerialName("NetworkID") val networkId: String? = null,
    @SerialName("Gateway") val gateway: String? = null,
    @SerialName("IPAddress") val ipAddress: String? = null,
    @SerialName("IPPrefixLen") val ipPrefixLen: Int? = null,
    @SerialName("MacAddress") val macAddress: String? = null
)
