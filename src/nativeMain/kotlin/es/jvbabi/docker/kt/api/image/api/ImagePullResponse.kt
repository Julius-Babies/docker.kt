package es.jvbabi.docker.kt.api.image.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator


@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
internal sealed class DockerImagePullApiStatus {
    @Serializable
    @SerialName("Pulling fs layer")
    data class PullingFsLayer(
        @SerialName("id") val id: String
    ) : DockerImagePullApiStatus()

    @Serializable
    @SerialName("Downloading")
    data class Downloading(
        @SerialName("id") val id: String,
        @SerialName("progressDetail") val progressDetail: ProgressDetail
    ): DockerImagePullApiStatus() {
        @Serializable
        data class ProgressDetail(
            @SerialName("current") val current: Long,
            @SerialName("total") val total: Long
        )
    }

    @Serializable
    @SerialName("Extracting")
    data class Extracting(
        @SerialName("id") val id: String,
        @SerialName("progressDetail") val progressDetail: ProgressDetail
    ): DockerImagePullApiStatus() {
        @Serializable
        data class ProgressDetail(
            @SerialName("current") val current: Long,
            @SerialName("units") val unit: String
        )
    }

    @Serializable
    @SerialName("Pull complete")
    data class DownloadComplete(
        @SerialName("id") val id: String
    ): DockerImagePullApiStatus()
}