package docker.api

import docker.client.HttpDockerClient
import docker.exceptions.DockerException
import docker.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * API for Docker volume operations.
 * Provides methods to manage Docker volumes.
 */
class VolumeApi(private val client: HttpDockerClient) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Lists all Docker volumes.
     * 
     * @param filters Filters to apply (e.g., "dangling=true")
     * @return List of volumes
     * @throws DockerException if the operation fails
     */
    suspend fun list(filters: String? = null): List<Volume> {
        val query = if (filters != null) "?filters=$filters" else ""
        val response = client.get("/volumes$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to list volumes: ${response.statusCode} - ${response.body}")
        }
        
        val listResponse: VolumeListResponse = json.decodeFromString(response.body)
        return listResponse.volumes ?: emptyList()
    }
    
    /**
     * Creates a new Docker volume.
     * 
     * @param request Volume creation configuration
     * @return Created volume
     * @throws DockerException if the operation fails
     */
    suspend fun create(request: VolumeCreateRequest): Volume {
        val body = json.encodeToString(request)
        val response = client.post("/volumes/create", body)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to create volume: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Inspects a Docker volume.
     * 
     * @param name Volume name
     * @return Detailed volume information
     * @throws DockerException if the volume is not found or operation fails
     */
    suspend fun inspect(name: String): Volume {
        val response = client.get("/volumes/$name")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to inspect volume: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Removes a Docker volume.
     * 
     * @param name Volume name
     * @param force Force removal
     * @throws DockerException if the operation fails
     */
    suspend fun remove(name: String, force: Boolean = false) {
        val query = if (force) "?force=true" else ""
        val response = client.delete("/volumes/$name$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to remove volume: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Prunes unused volumes.
     * 
     * @param filters Filters to apply
     * @return Prune response with deleted volumes and space reclaimed
     * @throws DockerException if the operation fails
     */
    suspend fun prune(filters: String? = null): VolumePruneResponse {
        val query = if (filters != null) "?filters=$filters" else ""
        val response = client.post("/volumes/prune$query")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to prune volumes: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
}

/**
 * DSL builder for volume creation.
 */
class VolumeBuilder {
    var name: String? = null
    var driver: String = "local"
    var driverOpts: MutableMap<String, String> = mutableMapOf()
    var labels: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Adds a driver option.
     */
    fun driverOption(key: String, value: String) {
        driverOpts[key] = value
    }
    
    /**
     * Adds a label.
     */
    fun label(key: String, value: String) {
        labels[key] = value
    }
    
    fun build(): VolumeCreateRequest = VolumeCreateRequest(
        name = name,
        driver = driver,
        driverOpts = driverOpts.ifEmpty { null },
        labels = labels.ifEmpty { null }
    )
}

/**
 * DSL function for creating volumes.
 * 
 * Example:
 * ```
 * val volume = volumeApi.createWith {
 *     name = "my-volume"
 *     driver = "local"
 *     label("app", "myapp")
 * }
 * ```
 */
suspend fun VolumeApi.createWith(block: VolumeBuilder.() -> Unit): Volume {
    val builder = VolumeBuilder()
    builder.block()
    val request = builder.build()
    return create(request)
}

/**
 * DSL builder for volume list options.
 */
class VolumeListBuilder {
    var filters: String? = null
    
    fun build(): VolumeListOptions = VolumeListOptions(filters)
}

/**
 * Options for listing volumes.
 */
data class VolumeListOptions(
    val filters: String? = null
)

/**
 * DSL function for listing volumes.
 * 
 * Example:
 * ```
 * val volumes = volumeApi.listWith {
 *     filters = "dangling=true"
 * }
 * ```
 */
suspend fun VolumeApi.listWith(block: VolumeListBuilder.() -> Unit): List<Volume> {
    val builder = VolumeListBuilder()
    builder.block()
    val options = builder.build()
    return list(filters = options.filters)
}
