package es.jvbabi.docker.kt.api.info
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DockerInfo(
    @SerialName("ID") val id: String,
    @SerialName("Containers") val containers: Int,
    @SerialName("ContainersRunning") val containersRunning: Int,
    @SerialName("ContainersPaused") val containersPaused: Int,
    @SerialName("ContainersStopped") val containersStopped: Int,
    @SerialName("Images") val images: Int,
    @SerialName("Driver") val driver: String,
    @SerialName("DriverStatus") val driverStatus: List<List<String>> = emptyList(),
    @SerialName("Plugins") val plugins: Plugins,
    @SerialName("MemoryLimit") val memoryLimit: Boolean,
    @SerialName("SwapLimit") val swapLimit: Boolean,
    @SerialName("CpuCfsPeriod") val cpuCfsPeriod: Boolean,
    @SerialName("CpuCfsQuota") val cpuCfsQuota: Boolean,
    @SerialName("CPUShares") val cpuShares: Boolean,
    @SerialName("CPUSet") val cpuSet: Boolean,
    @SerialName("PidsLimit") val pidsLimit: Boolean,
    @SerialName("IPv4Forwarding") val ipv4Forwarding: Boolean,
    @SerialName("Debug") val debug: Boolean,
    @SerialName("NFd") val nFd: Int,
    @SerialName("OomKillDisable") val oomKillDisable: Boolean,
    @SerialName("NGoroutines") val nGoroutines: Int,
    @SerialName("SystemTime") val systemTime: String,
    @SerialName("LoggingDriver") val loggingDriver: String,
    @SerialName("CgroupDriver") val cgroupDriver: String,
    @SerialName("CgroupVersion") val cgroupVersion: String,
    @SerialName("NEventsListener") val nEventsListener: Int,
    @SerialName("KernelVersion") val kernelVersion: String,
    @SerialName("OperatingSystem") val operatingSystem: String,
    @SerialName("OSVersion") val osVersion: String,
    @SerialName("OSType") val osType: String,
    @SerialName("Architecture") val architecture: String,
    @SerialName("IndexServerAddress") val indexServerAddress: String,
    @SerialName("RegistryConfig") val registryConfig: RegistryConfig,
    @SerialName("NCPU") val nCpu: Int,
    @SerialName("MemTotal") val memTotal: Long,
    @SerialName("GenericResources") val genericResources: JsonElement? = null,
    @SerialName("DockerRootDir") val dockerRootDir: String,
    @SerialName("HttpProxy") val httpProxy: String? = null,
    @SerialName("HttpsProxy") val httpsProxy: String? = null,
    @SerialName("NoProxy") val noProxy: String? = null,
    @SerialName("Name") val name: String,
    @SerialName("Labels") val labels: List<String> = emptyList(),
    @SerialName("ExperimentalBuild") val experimentalBuild: Boolean,
    @SerialName("ServerVersion") val serverVersion: String,
    @SerialName("Runtimes") val runtimes: Map<String, RuntimeInfo> = emptyMap(),
    @SerialName("DefaultRuntime") val defaultRuntime: String,
    @SerialName("Swarm") val swarm: SwarmInfo,
    @SerialName("LiveRestoreEnabled") val liveRestoreEnabled: Boolean,
    @SerialName("Isolation") val isolation: String,
    @SerialName("InitBinary") val initBinary: String,
    @SerialName("ContainerdCommit") val containerdCommit: CommitInfo,
    @SerialName("RuncCommit") val runcCommit: CommitInfo,
    @SerialName("InitCommit") val initCommit: CommitInfo,
    @SerialName("SecurityOptions") val securityOptions: List<String> = emptyList(),
    @SerialName("CDISpecDirs") val cdiSpecDirs: List<String> = emptyList(),
    @SerialName("Warnings") val warnings: String? = null,

    // New fields
    @SerialName("FirewallBackend") val firewallBackend: FirewallBackend? = null,
    @SerialName("DiscoveredDevices") val discoveredDevices: List<DiscoveredDevice>? = null,
    @SerialName("Containerd") val containerd: ContainerdInfo? = null
)

@Serializable
data class Plugins(
    @SerialName("Volume") val volume: List<String> = emptyList(),
    @SerialName("Network") val network: List<String> = emptyList(),
    @SerialName("Authorization") val authorization: JsonElement? = null,
    @SerialName("Log") val log: List<String> = emptyList()
)

@Serializable
data class RegistryConfig(
    @SerialName("IndexConfigs") val indexConfigs: Map<String, IndexConfig> = emptyMap(),
    @SerialName("InsecureRegistryCIDRs") val insecureRegistryCidrs: List<String> = emptyList(),
    @SerialName("Mirrors") val mirrors: JsonElement? = null
)

@Serializable
data class IndexConfig(
    @SerialName("Mirrors") val mirrors: List<String> = emptyList(),
    @SerialName("Name") val name: String,
    @SerialName("Official") val official: Boolean,
    @SerialName("Secure") val secure: Boolean
)

@Serializable
data class RuntimeInfo(
    @SerialName("path") val path: String,
    @SerialName("status") val status: JsonElement? = null,
    @SerialName("annotations") val annotations: JsonElement? = null,
    @SerialName("potentiallyUnsafeConfigAnnotations") val potentiallyUnsafeConfigAnnotations: List<String>? = null
)

@Serializable
data class SwarmInfo(
    @SerialName("NodeID") val nodeId: String? = null,
    @SerialName("NodeAddr") val nodeAddr: String? = null,
    @SerialName("LocalNodeState") val localNodeState: String,
    @SerialName("ControlAvailable") val controlAvailable: Boolean,
    @SerialName("Error") val error: String? = null,
    @SerialName("RemoteManagers") val remoteManagers: JsonElement? = null
)

@Serializable
data class CommitInfo(
    @SerialName("ID") val id: String,
    @SerialName("Expected") val expected: String? = null
)

@Serializable
data class FirewallBackend(
    @SerialName("Driver") val driver: String
)

@Serializable
data class DiscoveredDevice(
    @SerialName("Source") val source: String,
    @SerialName("ID") val id: String
)

@Serializable
data class ContainerdInfo(
    @SerialName("Address") val address: String,
    @SerialName("Namespaces") val namespaces: ContainerdNamespaces
)

@Serializable
data class ContainerdNamespaces(
    @SerialName("Containers") val containers: String,
    @SerialName("Plugins") val plugins: String
)
