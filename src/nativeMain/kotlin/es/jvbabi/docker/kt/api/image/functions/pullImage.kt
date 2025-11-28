package es.jvbabi.docker.kt.api.image.functions

import es.jvbabi.docker.kt.api.image.ImageApi.Companion.repositoryFromImage
import es.jvbabi.docker.kt.api.image.ImageApi.Companion.tagFromImage
import es.jvbabi.docker.kt.api.image.ImagePullStatus
import es.jvbabi.docker.kt.api.image.api.DockerImagePullApiStatus
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json

internal suspend fun pullImage(
    dockerClient: DockerClient,
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

    var hasFoundAllLayers = false

    val json = Json { ignoreUnknownKeys = true }
    val layerIds = mutableListOf<String>()

    dockerClient.socket.preparePost(url.build()).execute { response ->
        val channel: ByteReadChannel = response.body()

        while (!channel.isClosedForRead) {
            val line: String? = channel.readUTF8Line()
            if (line != null) {
                try {
                    val status = json.decodeFromString<DockerImagePullApiStatus>(line)
                    if (status is DockerImagePullApiStatus.PullingFsLayer) {
                        layerIds.add(status.id)
                    } else {
                        if (!hasFoundAllLayers) {
                            hasFoundAllLayers = true
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