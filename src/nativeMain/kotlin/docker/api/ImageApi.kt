package docker.api

import docker.client.HttpDockerClient
import docker.client.HttpResponse
import docker.exceptions.DockerException
import docker.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * API for Docker image operations.
 * Provides methods to manage Docker images.
 */
class ImageApi(private val client: HttpDockerClient) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Lists all Docker images.
     * 
     * @param all Show all images (including intermediate images)
     * @param filters Filters to apply (e.g., "dangling=true")
     * @return List of images
     * @throws DockerException if the operation fails
     */
    suspend fun list(all: Boolean = false, filters: String? = null): List<Image> {
        val params = mutableMapOf<String, String>()
        if (all) params["all"] = "true"
        if (filters != null) params["filters"] = filters
        
        val response = client.get("/images/json", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to list images: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Inspects a Docker image.
     * 
     * @param name Image name or ID
     * @return Detailed image information
     * @throws DockerException if the image is not found or operation fails
     */
    suspend fun inspect(name: String): ImageInspect {
        val response = client.get("/images/$name/json")
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to inspect image: ${response.statusCode} - ${response.body}")
        }
        
        return json.decodeFromString(response.body)
    }
    
    /**
     * Removes a Docker image.
     * 
     * @param name Image name or ID
     * @param force Force removal of the image
     * @param noprune Do not delete untagged parent images
     * @throws DockerException if the operation fails
     */
    suspend fun remove(name: String, force: Boolean = false, noprune: Boolean = false) {
        val params = mutableMapOf<String, String>()
        if (force) params["force"] = "true"
        if (noprune) params["noprune"] = "true"
        
        val response = client.delete("/images/$name", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to remove image: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Pulls a Docker image from a registry.
     * 
     * @param name Image name (e.g., "nginx" or "library/nginx")
     * @param tag Image tag (default: "latest")
     * @param registry Registry URL (optional)
     * @throws DockerException if the operation fails
     */
    suspend fun pull(name: String, tag: String = "latest", registry: String? = null) {
        val fromImage = if (registry != null) "$registry/$name" else name
        val params = mapOf(
            "fromImage" to fromImage,
            "tag" to tag
        )
        val response = client.post("/images/create", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to pull image: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Tags a Docker image.
     * 
     * @param name Image name or ID to tag
     * @param repo Repository name for the tag
     * @param tag Tag name (default: "latest")
     * @throws DockerException if the operation fails
     */
    suspend fun tag(name: String, repo: String, tag: String = "latest") {
        val params = mapOf(
            "repo" to repo,
            "tag" to tag
        )
        val response = client.post("/images/$name/tag", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to tag image: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Pushes a Docker image to a registry.
     * 
     * @param name Image name or ID to push
     * @param tag Image tag (default: "latest")
     * @throws DockerException if the operation fails
     */
    suspend fun push(name: String, tag: String = "latest") {
        val params = mapOf("tag" to tag)
        val response = client.post("/images/$name/push", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to push image: ${response.statusCode} - ${response.body}")
        }
    }
    
    /**
     * Prunes unused images.
     * 
     * @param filters Filters to apply (e.g., "dangling=true")
     * @return Space reclaimed in bytes
     * @throws DockerException if the operation fails
     */
    suspend fun prune(filters: String? = null): Long {
        val params = mutableMapOf<String, String>()
        if (filters != null) params["filters"] = filters
        
        val response = client.post("/images/prune", params)
        
        if (!response.isSuccessful()) {
            throw DockerException("Failed to prune images: ${response.statusCode} - ${response.body}")
        }
        
        // Parse response to get space reclaimed
        // For now, return 0 as we need a proper response model
        return 0
    }
}

/**
 * DSL builder for image operations.
 */
class ImageBuilder {
    var all: Boolean = false
    var filters: String? = null
    
    fun build(): ImageQueryOptions = ImageQueryOptions(all, filters)
}

/**
 * Options for querying images.
 */
data class ImageQueryOptions(
    val all: Boolean = false,
    val filters: String? = null
)

/**
 * DSL function for listing images.
 * 
 * Example:
 * ```
 * val images = imageApi.listWith {
 *     all = true
 *     filters = "dangling=true"
 * }
 * ```
 */
suspend fun ImageApi.listWith(block: ImageBuilder.() -> Unit): List<Image> {
    val builder = ImageBuilder()
    builder.block()
    val options = builder.build()
    return list(all = options.all, filters = options.filters)
}
