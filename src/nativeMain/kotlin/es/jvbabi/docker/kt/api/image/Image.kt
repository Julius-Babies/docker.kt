package es.jvbabi.docker.kt.api.image

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DockerImage(
    @SerialName("Id")
    val id: String,

    @SerialName("ParentId")
    val parentId: String,

    @SerialName("RepoTags")
    val repoTags: List<String>,

    @SerialName("RepoDigests")
    val repoDigests: List<String>,

    @SerialName("Created")
    val created: Long,

    @SerialName("Size")
    val size: Long,

    @SerialName("SharedSize")
    val sharedSize: Long,

    @SerialName("Labels")
    val labels: Map<String, String>? = null,

    @SerialName("Containers")
    val containers: Int,

    @SerialName("Descriptor")
    val descriptor: Descriptor? = null,
)

@Serializable
data class Descriptor(
    @SerialName("mediaType")
    val mediaType: String,

    @SerialName("digest")
    val digest: String,

    @SerialName("size")
    val size: Long,

    @SerialName("urls")
    val urls: List<String> = emptyList()
)
