package docker.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Docker image.
 */
@Serializable
data class Image(
    @SerialName("Id") val id: String,
    @SerialName("RepoTags") val repoTags: List<String>? = null,
    @SerialName("RepoDigests") val repoDigests: List<String>? = null,
    @SerialName("Created") val created: Long,
    @SerialName("Size") val size: Long,
    @SerialName("VirtualSize") val virtualSize: Long,
    @SerialName("Labels") val labels: Map<String, String>? = null
)

/**
 * Detailed information about a Docker image.
 */
@Serializable
data class ImageInspect(
    @SerialName("Id") val id: String,
    @SerialName("RepoTags") val repoTags: List<String>? = null,
    @SerialName("RepoDigests") val repoDigests: List<String>? = null,
    @SerialName("Created") val created: String,
    @SerialName("Size") val size: Long,
    @SerialName("Architecture") val architecture: String? = null,
    @SerialName("Os") val os: String? = null,
    @SerialName("Config") val config: ImageConfig? = null
)

/**
 * Configuration of a Docker image.
 */
@Serializable
data class ImageConfig(
    @SerialName("Hostname") val hostname: String? = null,
    @SerialName("User") val user: String? = null,
    @SerialName("ExposedPorts") val exposedPorts: Map<String, Map<String, String>>? = null,
    @SerialName("Env") val env: List<String>? = null,
    @SerialName("Cmd") val cmd: List<String>? = null,
    @SerialName("WorkingDir") val workingDir: String? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null
)
