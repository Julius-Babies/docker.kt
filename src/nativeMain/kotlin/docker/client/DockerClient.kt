package docker.client

import docker.api.ContainerApi
import docker.api.ImageApi
import docker.api.NetworkApi
import docker.api.VolumeApi
import docker.exceptions.DockerException

/**
 * Main Docker client for interacting with the Docker daemon.
 * Provides access to Docker API through Unix domain sockets.
 */
class DockerClient(
    private val config: DockerClientConfig = DockerClientConfig.default()
) {
    private val httpClient = HttpDockerClient(config)
    
    /**
     * API for managing Docker images.
     */
    val images = ImageApi(httpClient)
    
    /**
     * API for managing Docker containers.
     */
    val containers = ContainerApi(httpClient)
    
    /**
     * API for managing Docker volumes.
     */
    val volumes = VolumeApi(httpClient)
    
    /**
     * API for managing Docker networks.
     */
    val networks = NetworkApi(httpClient)
    
    /**
     * Pings the Docker daemon to check connectivity.
     * @return true if daemon is reachable, false otherwise
     */
    suspend fun ping(): Boolean {
        return try {
            val response = httpClient.get("/_ping")
            response.isSuccessful()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets Docker daemon version information.
     */
    suspend fun version(): String {
        val response = httpClient.get("/version")
        if (!response.isSuccessful()) {
            throw DockerException("Failed to get Docker version: ${response.statusCode}")
        }
        return response.body
    }
    
    /**
     * Gets Docker system information.
     */
    suspend fun info(): String {
        val response = httpClient.get("/info")
        if (!response.isSuccessful()) {
            throw DockerException("Failed to get Docker info: ${response.statusCode}")
        }
        return response.body
    }
    
    /**
     * Gets the socket path being used by this client.
     */
    fun getSocketPath(): String = config.socketPath
    
    /**
     * Closes the client and releases resources.
     */
    fun close() {
        httpClient.close()
    }
}
