package api.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DockerManifestIndex(
    @SerialName("manifests")
    val manifests: List<Manifest>,

    @SerialName("mediaType")
    val mediaType: String,

    @SerialName("schemaVersion")
    val schemaVersion: Int
)

@Serializable
data class Manifest(
    @SerialName("annotations")
    val annotations: Map<String, String>? = null,

    @SerialName("digest")
    val digest: String,

    @SerialName("mediaType")
    val mediaType: String,

    @SerialName("platform")
    val platform: Platform? = null,

    @SerialName("size")
    val size: Long
)

@Serializable
data class Platform(
    @SerialName("architecture")
    val architecture: String,

    @SerialName("os")
    val os: String,

    @SerialName("variant")
    val variant: String? = null
)
