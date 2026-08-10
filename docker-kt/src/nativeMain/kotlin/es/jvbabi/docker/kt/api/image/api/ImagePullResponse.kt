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
        // Docker reports whatever it knows at that moment: early messages carry neither figure.
        data class ProgressDetail(
            @SerialName("current") val current: Long = 0,
            @SerialName("total") val total: Long = 0
        )
    }

    @Serializable
    @SerialName("Extracting")
    data class Extracting(
        @SerialName("id") val id: String,
        @SerialName("progressDetail") val progressDetail: ProgressDetail
    ): DockerImagePullApiStatus() {
        @Serializable
        // "units" only shows up once Docker has a unit to name, "current" not at all on some
        // daemons - a pull of a cached layer sends progressDetail as an empty object.
        data class ProgressDetail(
            @SerialName("current") val current: Long = 0,
            @SerialName("units") val unit: String = ""
        )
    }

    @Serializable
    @SerialName("Pull complete")
    data class DownloadComplete(
        @SerialName("id") val id: String
    ): DockerImagePullApiStatus()
}