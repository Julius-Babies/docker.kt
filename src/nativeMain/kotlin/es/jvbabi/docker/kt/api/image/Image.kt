package es.jvbabi.docker.kt.api.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DockerImage(
    @SerialName("Containers")
    val containers: Int,

    @SerialName("Created")
    val created: Long,

    @SerialName("Id")
    val id: String,

    @SerialName("Labels")
    val labels: Map<String, String>? = null,

    @SerialName("ParentId")
    val parentId: String,

    @SerialName("Descriptor")
    val descriptor: Descriptor,

    @SerialName("RepoDigests")
    val repoDigests: List<String>,

    @SerialName("RepoTags")
    val repoTags: List<String>,

    @SerialName("SharedSize")
    val sharedSize: Long,

    @SerialName("Size")
    val size: Long
)

@Serializable
data class Descriptor(
    @SerialName("mediaType")
    val mediaType: String,

    @SerialName("digest")
    val digest: String,

    @SerialName("size")
    val size: Long
)
