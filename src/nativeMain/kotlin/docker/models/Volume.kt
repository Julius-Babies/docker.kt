package docker.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Docker volume.
 */
@Serializable
data class Volume(
    @SerialName("Name") val name: String,
    @SerialName("Driver") val driver: String,
    @SerialName("Mountpoint") val mountpoint: String,
    @SerialName("CreatedAt") val createdAt: String? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null,
    @SerialName("Scope") val scope: String? = null,
    @SerialName("Options") val options: Map<String, String>? = null
)

/**
 * Response from volume list operation.
 */
@Serializable
data class VolumeListResponse(
    @SerialName("Volumes") val volumes: List<Volume>? = null,
    @SerialName("Warnings") val warnings: List<String>? = null
)

/**
 * Configuration for creating a volume.
 */
@Serializable
data class VolumeCreateRequest(
    @SerialName("Name") val name: String? = null,
    @SerialName("Driver") val driver: String = "local",
    @SerialName("DriverOpts") val driverOpts: Map<String, String>? = null,
    @SerialName("Labels") val labels: Map<String, String>? = null
)

/**
 * Response from volume prune operation.
 */
@Serializable
data class VolumePruneResponse(
    @SerialName("VolumesDeleted") val volumesDeleted: List<String>? = null,
    @SerialName("SpaceReclaimed") val spaceReclaimed: Long? = null
)
