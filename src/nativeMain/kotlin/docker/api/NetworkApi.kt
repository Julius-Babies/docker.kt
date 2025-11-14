package docker.api

import docker.client.HttpDockerClient
import docker.exceptions.DockerException
import docker.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * API for Docker network operations.
 * Provides methods to manage Docker networks.
 */
class NetworkApi(private val client: HttpDockerClient) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Lists all Docker networks.
     * 
     * @param filters Filters to apply
     * @return List of networks
     * @throws DockerException if the operation fails
     */
    suspend fun list(filters: String? = null): List<Network> {
        val params = mutableMapOf<String, String>()
        if (filters != null) params["filters"] = filters
        
        val response = client.get("/networks", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to list networks: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Creates a new Docker network.
     * 
     * @param request Network creation configuration
     * @return Network creation response with ID
     * @throws DockerException if the operation fails
     */
    suspend fun create(request: NetworkCreateRequest): NetworkCreateResponse {
        val body = json.encodeToString(request)
        val response = client.post("/networks/create", body)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to create network: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Inspects a Docker network.
     * 
     * @param id Network ID or name
     * @param verbose Show detailed information
     * @param scope Filter by scope (local, global, swarm)
     * @return Detailed network information
     * @throws DockerException if the network is not found or operation fails
     */
    suspend fun inspect(id: String, verbose: Boolean = false, scope: String? = null): Network {
        val params = mutableMapOf<String, String>()
        if (verbose) params["verbose"] = "true"
        if (scope != null) params["scope"] = scope
        
        val response = client.get("/networks/$id", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to inspect network: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Removes a Docker network.
     * 
     * @param id Network ID or name
     * @throws DockerException if the operation fails
     */
    suspend fun remove(id: String) {
        val response = client.delete("/networks/$id")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to remove network: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Connects a container to a network.
     * 
     * @param networkId Network ID or name
     * @param request Connection configuration
     * @throws DockerException if the operation fails
     */
    suspend fun connect(networkId: String, request: NetworkConnectRequest) {
        val body = json.encodeToString(request)
        val response = client.post("/networks/$networkId/connect", body)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to connect container to network: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Disconnects a container from a network.
     * 
     * @param networkId Network ID or name
     * @param request Disconnection configuration
     * @throws DockerException if the operation fails
     */
    suspend fun disconnect(networkId: String, request: NetworkDisconnectRequest) {
        val body = json.encodeToString(request)
        val response = client.post("/networks/$networkId/disconnect", body)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to disconnect container from network: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Prunes unused networks.
     * 
     * @param filters Filters to apply
     * @return Prune response with deleted networks
     * @throws DockerException if the operation fails
     */
    suspend fun prune(filters: String? = null): NetworkPruneResponse {
        val params = mutableMapOf<String, String>()
        if (filters != null) params["filters"] = filters
        
        val response = client.post("/networks/prune", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to prune networks: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
}

/**
 * DSL builder for network creation.
 */
class NetworkBuilder {
    lateinit var name: String
    var driver: String = "bridge"
    var internal: Boolean? = null
    var attachable: Boolean? = null
    var enableIPv6: Boolean? = null
    var options: MutableMap<String, String> = mutableMapOf()
    var labels: MutableMap<String, String> = mutableMapOf()
    
    private var ipamBuilder: IPAMBuilder? = null
    
    /**
     * Adds a driver option.
     */
    fun option(key: String, value: String) {
        options[key] = value
    }
    
    /**
     * Adds a label.
     */
    fun label(key: String, value: String) {
        labels[key] = value
    }
    
    /**
     * Configures IPAM (IP Address Management).
     */
    fun ipam(block: IPAMBuilder.() -> Unit) {
        val builder = IPAMBuilder()
        builder.block()
        ipamBuilder = builder
    }
    
    fun build(): NetworkCreateRequest = NetworkCreateRequest(
        name = name,
        driver = driver,
        internal = internal,
        attachable = attachable,
        enableIPv6 = enableIPv6,
        ipam = ipamBuilder?.build(),
        options = options.ifEmpty { null },
        labels = labels.ifEmpty { null }
    )
}

/**
 * DSL builder for IPAM configuration.
 */
class IPAMBuilder {
    var driver: String = "default"
    var config: MutableList<IPAMConfigEntry> = mutableListOf()
    var options: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Adds an IPAM configuration entry.
     */
    fun subnet(subnet: String, gateway: String? = null, ipRange: String? = null) {
        config.add(IPAMConfigEntry(
            subnet = subnet,
            gateway = gateway,
            ipRange = ipRange
        ))
    }
    
    /**
     * Adds an IPAM option.
     */
    fun option(key: String, value: String) {
        options[key] = value
    }
    
    fun build(): IPAM = IPAM(
        driver = driver,
        config = config.ifEmpty { null },
        options = options.ifEmpty { null }
    )
}

/**
 * DSL function for creating networks.
 * 
 * Example:
 * ```
 * val network = networkApi.createWith {
 *     name = "my-network"
 *     driver = "bridge"
 *     ipam {
 *         subnet("172.20.0.0/16", gateway = "172.20.0.1")
 *     }
 *     label("app", "myapp")
 * }
 * ```
 */
suspend fun NetworkApi.createWith(block: NetworkBuilder.() -> Unit): NetworkCreateResponse {
    val builder = NetworkBuilder()
    builder.block()
    val request = builder.build()
    return create(request)
}

/**
 * DSL function for connecting a container to a network.
 * 
 * Example:
 * ```
 * networkApi.connectContainer("my-network", "my-container") {
 *     ipv4Address = "172.20.0.2"
 *     aliases = listOf("web", "api")
 * }
 * ```
 */
suspend fun NetworkApi.connectContainer(
    networkId: String,
    containerId: String,
    block: (ConnectBuilder.() -> Unit)? = null
) {
    val builder = ConnectBuilder()
    block?.invoke(builder)
    
    val endpointConfig = if (builder.ipv4Address != null || builder.aliases != null) {
        val ipamConfig = if (builder.ipv4Address != null) {
            IPAMConfig(builder.ipv4Address)
        } else null
        EndpointConfig(ipamConfig, builder.aliases)
    } else null
    
    val request = NetworkConnectRequest(containerId, endpointConfig)
    connect(networkId, request)
}

/**
 * DSL builder for network connection.
 */
class ConnectBuilder {
    var ipv4Address: String? = null
    var aliases: List<String>? = null
}

/**
 * DSL function for disconnecting a container from a network.
 * 
 * Example:
 * ```
 * networkApi.disconnectContainer("my-network", "my-container", force = true)
 * ```
 */
suspend fun NetworkApi.disconnectContainer(
    networkId: String,
    containerId: String,
    force: Boolean = false
) {
    val request = NetworkDisconnectRequest(containerId, force)
    disconnect(networkId, request)
}

/**
 * DSL builder for network list options.
 */
class NetworkListBuilder {
    var filters: String? = null
    
    fun build(): NetworkListOptions = NetworkListOptions(filters)
}

/**
 * Options for listing networks.
 */
data class NetworkListOptions(
    val filters: String? = null
)

/**
 * DSL function for listing networks.
 * 
 * Example:
 * ```
 * val networks = networkApi.listWith {
 *     filters = "driver=bridge"
 * }
 * ```
 */
suspend fun NetworkApi.listWith(block: NetworkListBuilder.() -> Unit): List<Network> {
    val builder = NetworkListBuilder()
    builder.block()
    val options = builder.build()
    return list(filters = options.filters)
}
