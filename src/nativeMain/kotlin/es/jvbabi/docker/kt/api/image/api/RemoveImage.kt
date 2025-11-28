package es.jvbabi.docker.kt.api.image.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DockerRemoveImageResponse(
    @SerialName("Untagged") val untagged: String? = null,
    @SerialName("Deleted") val deleted: String? = null
)