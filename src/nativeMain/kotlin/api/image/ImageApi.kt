package api.image

import docker.DockerClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

class ImageApi internal constructor(private val client: DockerClient) {
    suspend fun getImages(): List<DockerImage> {
        val response = client.socket.get("/images/json")
        return response.body()
    }

    suspend fun pull(
        image: String,
        beforeDownload: (layerHashes: List<String>) -> Unit = {},
        onDownload: (layerHash: String, status: ImagePullStatus) -> Unit
    ) {
        val url = URLBuilder().apply {
            protocol = URLProtocol.HTTP
            host = "localhost"
            pathSegments = listOf("images", "create")
            parameters.append("fromImage", repositoryFromImage(image))
            parameters.append("tag", tagFromImage(image))
        }

        val STAGE_FINDING_LAYERS = 0

        var currentStage = STAGE_FINDING_LAYERS

        val json = Json { ignoreUnknownKeys = true }
        val layerIds = mutableListOf<String>()

        client.socket.preparePost(url.build()).execute { response ->
            val channel: ByteReadChannel = response.body()

            while (!channel.isClosedForRead) {
                val line: String? = channel.readUTF8Line()
                if (line != null) {
//                    println(line)
                    try {
                        val status = json.decodeFromString<DockerImagePullApiStatus>(line)
                        if (status is DockerImagePullApiStatus.PullingFsLayer) {
                            layerIds.add(status.id)
                        } else {
                            if (currentStage == STAGE_FINDING_LAYERS) {
                                currentStage += 1
                                beforeDownload(layerIds)
                            }

                            when (status) {
                                is DockerImagePullApiStatus.Downloading -> {
                                    onDownload(status.id, ImagePullStatus.Pulling(status.progressDetail.total, status.progressDetail.current))
                                }
                                is DockerImagePullApiStatus.DownloadComplete -> {
                                    onDownload(status.id, ImagePullStatus.Downloaded)
                                }
                                is DockerImagePullApiStatus.Extracting -> {
                                    onDownload(
                                        status.id,
                                        ImagePullStatus.Extracting(
                                            status.id,
                                            status.progressDetail.current,
                                            status.progressDetail.unit
                                        )
                                    )
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("is not found in the polymorphic scope") != true) {
                            throw e
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun repositoryFromImage(image: String): String {
            val imageWithoutTag = image.substringBefore(":")
            if (imageWithoutTag.contains("/")) return imageWithoutTag
            return "library/$imageWithoutTag"
        }

        fun tagFromImage(image: String): String {
            return image.substringAfter(":", "latest")
        }
    }
}

sealed class ImagePullStatus {
    data class Pulling(val bytesTotal: Long, val bytesPulled: Long): ImagePullStatus()
    data class Extracting(val layerHash: String, val current: Long, val unit: String): ImagePullStatus()
    data object Downloaded: ImagePullStatus()
}

@Serializable
@JsonClassDiscriminator("status")
sealed class DockerImagePullApiStatus {
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
    @SerialName("Download complete")
    data class DownloadComplete(
        @SerialName("id") val id: String
    ): DockerImagePullApiStatus()
}