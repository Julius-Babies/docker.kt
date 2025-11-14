package docker.api

import docker.client.HttpDockerClient
import docker.exceptions.DockerException
import docker.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * API for Docker container operations.
 * Provides methods to manage Docker containers.
 */
class ContainerApi(private val client: HttpDockerClient) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Lists all Docker containers.
     * 
     * @param all Show all containers (including stopped ones)
     * @param limit Limit the number of results
     * @param size Show container sizes
     * @param filters Filters to apply
     * @return List of containers
     * @throws DockerException if the operation fails
     */
    suspend fun list(
        all: Boolean = false,
        limit: Int? = null,
        size: Boolean = false,
        filters: String? = null
    ): List<Container> {
        val params = mutableListOf<String>()
        if (all) params.add("all=true")
        if (limit != null) params.add("limit=$limit")
        if (size) params.add("size=true")
        if (filters != null) params.add("filters=$filters")
        
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val response = client.get("/containers/json$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to list containers: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Creates a new Docker container.
     * 
     * @param config Container configuration
     * @param name Optional container name
     * @return Container creation response with ID
     * @throws DockerException if the operation fails
     */
    suspend fun create(config: ContainerConfig, name: String? = null): ContainerCreateResponse {
        val query = if (name != null) "?name=$name" else ""
        val body = json.encodeToString(config)
        val response = client.post("/containers/create$query", body)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to create container: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Starts a container.
     * 
     * @param id Container ID or name
     * @throws DockerException if the operation fails
     */
    suspend fun start(id: String) {
        val response = client.post("/containers/$id/start")
        
        if (!response.isSuccessful() && response.statusCode != 304) {
            // 304 means container was already started
            throw DockerException("Failed to start container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Stops a container.
     * 
     * @param id Container ID or name
     * @param timeout Time to wait before killing the container (seconds)
     * @throws DockerException if the operation fails
     */
    suspend fun stop(id: String, timeout: Int = 10) {
        val response = client.post("/containers/$id/stop?t=$timeout")
        
        if (!response.isSuccessful() && response.statusCode != 304) {
            // 304 means container was already stopped
            throw DockerException("Failed to stop container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Restarts a container.
     * 
     * @param id Container ID or name
     * @param timeout Time to wait before killing the container (seconds)
     * @throws DockerException if the operation fails
     */
    suspend fun restart(id: String, timeout: Int = 10) {
        val response = client.post("/containers/$id/restart?t=$timeout")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to restart container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Kills a running container.
     * 
     * @param id Container ID or name
     * @param signal Signal to send (default: SIGKILL)
     * @throws DockerException if the operation fails
     */
    suspend fun kill(id: String, signal: String = "SIGKILL") {
        val response = client.post("/containers/$id/kill?signal=$signal")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to kill container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Pauses a container.
     * 
     * @param id Container ID or name
     * @throws DockerException if the operation fails
     */
    suspend fun pause(id: String) {
        val response = client.post("/containers/$id/pause")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to pause container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Unpauses a container.
     * 
     * @param id Container ID or name
     * @throws DockerException if the operation fails
     */
    suspend fun unpause(id: String) {
        val response = client.post("/containers/$id/unpause")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to unpause container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Removes a container.
     * 
     * @param id Container ID or name
     * @param force Force removal of running container
     * @param volumes Remove associated volumes
     * @throws DockerException if the operation fails
     */
    suspend fun remove(id: String, force: Boolean = false, volumes: Boolean = false) {
        val params = mutableListOf<String>()
        if (force) params.add("force=true")
        if (volumes) params.add("v=true")
        
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val response = client.delete("/containers/$id$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to remove container: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Inspects a container.
     * 
     * @param id Container ID or name
     * @param size Include container size information
     * @return Detailed container information
     * @throws DockerException if the operation fails
     */
    suspend fun inspect(id: String, size: Boolean = false): ContainerInspect {
        val query = if (size) "?size=true" else ""
        val response = client.get("/containers/$id/json$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to inspect container: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Gets logs from a container.
     * 
     * @param id Container ID or name
     * @param stdout Include stdout
     * @param stderr Include stderr
     * @param tail Number of lines from the end (default: all)
     * @param timestamps Include timestamps
     * @return Container logs as string
     * @throws DockerException if the operation fails
     */
    suspend fun logs(
        id: String,
        stdout: Boolean = true,
        stderr: Boolean = true,
        tail: Int? = null,
        timestamps: Boolean = false
    ): String {
        val params = mutableListOf<String>()
        if (stdout) params.add("stdout=true")
        if (stderr) params.add("stderr=true")
        if (tail != null) params.add("tail=$tail")
        if (timestamps) params.add("timestamps=true")
        
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val response = client.get("/containers/$id/logs$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to get container logs: ${response.statusCode} - ${response.body}")
        }
        
        return response.body
    }
    
    /**
     * Prunes stopped containers.
     * 
     * @param filters Filters to apply
     * @throws DockerException if the operation fails
     */
    suspend fun prune(filters: String? = null) {
        val query = if (filters != null) "?filters=$filters" else ""
        val response = client.post("/containers/prune$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to prune containers: ${response.statusCode} - ${response.body}")
        }
    }
}

/**
 * DSL builder for container creation.
 */
class ContainerBuilder {
    lateinit var image: String
    var cmd: List<String>? = null
    var entrypoint: List<String>? = null
    var env: MutableList<String> = mutableListOf()
    var workingDir: String? = null
    var exposedPorts: MutableMap<String, Map<String, String>> = mutableMapOf()
    var labels: MutableMap<String, String> = mutableMapOf()
    
    private var hostConfigBuilder: HostConfigBuilder? = null
    private var networkingConfigBuilder: NetworkingConfigBuilder? = null
    
    /**
     * Sets command to run in the container.
     */
    fun command(vararg cmd: String) {
        this.cmd = cmd.toList()
    }
    
    /**
     * Adds an environment variable.
     */
    fun env(key: String, value: String) {
        env.add("$key=$value")
    }
    
    /**
     * Exposes a port.
     */
    fun expose(port: Int, protocol: String = "tcp") {
        exposedPorts["$port/$protocol"] = emptyMap()
    }
    
    /**
     * Adds a label.
     */
    fun label(key: String, value: String) {
        labels[key] = value
    }
    
    /**
     * Configures host settings.
     */
    fun host(block: HostConfigBuilder.() -> Unit) {
        val builder = HostConfigBuilder()
        builder.block()
        hostConfigBuilder = builder
    }
    
    /**
     * Configures networking.
     */
    fun network(block: NetworkingConfigBuilder.() -> Unit) {
        val builder = NetworkingConfigBuilder()
        builder.block()
        networkingConfigBuilder = builder
    }
    
    fun build(): ContainerConfig = ContainerConfig(
        image = image,
        cmd = cmd,
        entrypoint = entrypoint,
        env = env.ifEmpty { null },
        workingDir = workingDir,
        exposedPorts = exposedPorts.ifEmpty { null },
        labels = labels.ifEmpty { null },
        hostConfig = hostConfigBuilder?.build(),
        networkingConfig = networkingConfigBuilder?.build()
    )
}

/**
 * DSL builder for host configuration.
 */
class HostConfigBuilder {
    var binds: MutableList<String> = mutableListOf()
    var portBindings: MutableMap<String, List<PortBinding>> = mutableMapOf()
    var restartPolicy: String? = null
    var autoRemove: Boolean? = null
    var networkMode: String? = null
    var privileged: Boolean? = null
    var memory: Long? = null
    var memorySwap: Long? = null
    var cpuShares: Int? = null
    
    /**
     * Binds a volume.
     */
    fun bind(hostPath: String, containerPath: String, mode: String = "rw") {
        binds.add("$hostPath:$containerPath:$mode")
    }
    
    /**
     * Binds a port.
     */
    fun bindPort(containerPort: Int, hostPort: Int, hostIp: String = "0.0.0.0", protocol: String = "tcp") {
        val key = "$containerPort/$protocol"
        portBindings[key] = listOf(PortBinding(hostIp, hostPort.toString()))
    }
    
    fun build(): HostConfig {
        val policy = when (restartPolicy) {
            "always" -> RestartPolicy("always", 0)
            "unless-stopped" -> RestartPolicy("unless-stopped", 0)
            "on-failure" -> RestartPolicy("on-failure", 5)
            else -> null
        }
        
        return HostConfig(
            binds = binds.ifEmpty { null },
            portBindings = portBindings.ifEmpty { null },
            restartPolicy = policy,
            autoRemove = autoRemove,
            networkMode = networkMode,
            privileged = privileged,
            memory = memory,
            memorySwap = memorySwap,
            cpuShares = cpuShares
        )
    }
}

/**
 * DSL builder for networking configuration.
 */
class NetworkingConfigBuilder {
    var endpointsConfig: MutableMap<String, EndpointConfig> = mutableMapOf()
    
    /**
     * Connects to a network.
     */
    fun connectTo(networkName: String, ipv4Address: String? = null, aliases: List<String>? = null) {
        val ipamConfig = if (ipv4Address != null) IPAMConfig(ipv4Address) else null
        endpointsConfig[networkName] = EndpointConfig(ipamConfig, aliases)
    }
    
    fun build(): NetworkingConfig = NetworkingConfig(
        endpointsConfig = endpointsConfig.ifEmpty { null }
    )
}

/**
 * DSL function for creating containers.
 * 
 * Example:
 * ```
 * val container = containerApi.createWith("my-nginx") {
 *     image = "nginx:latest"
 *     expose(80)
 *     host {
 *         bindPort(80, 8080)
 *     }
 * }
 * ```
 */
suspend fun ContainerApi.createWith(name: String? = null, block: ContainerBuilder.() -> Unit): ContainerCreateResponse {
    val builder = ContainerBuilder()
    builder.block()
    val config = builder.build()
    return create(config, name)
}

/**
 * DSL builder for container list options.
 */
class ContainerListBuilder {
    var all: Boolean = false
    var limit: Int? = null
    var size: Boolean = false
    var filters: String? = null
    
    fun build(): ContainerListOptions = ContainerListOptions(all, limit, size, filters)
}

/**
 * Options for listing containers.
 */
data class ContainerListOptions(
    val all: Boolean = false,
    val limit: Int? = null,
    val size: Boolean = false,
    val filters: String? = null
)

/**
 * DSL function for listing containers.
 * 
 * Example:
 * ```
 * val containers = containerApi.listWith {
 *     all = true
 *     size = true
 * }
 * ```
 */
suspend fun ContainerApi.listWith(block: ContainerListBuilder.() -> Unit): List<Container> {
    val builder = ContainerListBuilder()
    builder.block()
    val options = builder.build()
    return list(
        all = options.all,
        limit = options.limit,
        size = options.size,
        filters = options.filters
    )
}
