package es.jvbabi.docker.kt.api.image.functions

import es.jvbabi.docker.kt.api.image.ImageRemoveStatus
import es.jvbabi.docker.kt.api.image.api.DockerRemoveImageResponse
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.delete

internal suspend fun removeImage(
    client: DockerClient,
    image: String,
    force: Boolean = false,
    deleteUntaggedParents: Boolean = true
): List<ImageRemoveStatus> {
    val images = client.images.getImages()
    val imageWithHash = images.find { it.id.substringAfter("sha256:") == image.substringAfter("sha256:") }
    val url = if (imageWithHash != null) {
        "/images/${imageWithHash.id}?force=${force}&noprune=${!deleteUntaggedParents}"
    } else {
        "/images/${image}?force=${force}&noprune=${!deleteUntaggedParents}"
    }

    val response = client.socket.delete(url)
    val data = response.body<List<DockerRemoveImageResponse>>()
    return data.map {
        ImageRemoveStatus(
            id = it.deleted ?: it.untagged!!,
            type = if (it.deleted != null) ImageRemoveStatus.Type.Deleted else ImageRemoveStatus.Type.Untagged
        )
    }
}