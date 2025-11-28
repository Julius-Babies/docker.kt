package es.jvbabi.docker.kt.api.image

import es.jvbabi.docker.kt.api.image.api.DockerImage
import es.jvbabi.docker.kt.api.image.functions.getImages
import es.jvbabi.docker.kt.api.image.functions.pullImage
import es.jvbabi.docker.kt.docker.DockerClient

class ImageApi internal constructor(private val client: DockerClient) {
    @Suppress("unused")
    suspend fun getImages(): List<DockerImage> = getImages(client)

    @Suppress("unused")
    suspend fun pull(
        image: String,
        beforeDownload: (layerHashes: List<String>) -> Unit = {},
        onDownload: (layerHash: String, status: ImagePullStatus) -> Unit
    ) = pullImage(client, image, beforeDownload, onDownload)

    @Suppress("unused")
    suspend fun removeImage(
        image: String,
        force: Boolean = false,
        deleteUntaggedParents: Boolean = true
    ): List<ImageRemoveStatus> = es.jvbabi.docker.kt.api.image.functions.removeImage(
        client = client,
        image = image,
        force = force,
        deleteUntaggedParents = deleteUntaggedParents
    )

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
