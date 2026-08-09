package es.jvbabi.docker.kt.api.image

import es.jvbabi.docker.kt.api.image.api.DockerImage
import es.jvbabi.docker.kt.api.image.functions.getImages
import es.jvbabi.docker.kt.api.image.functions.pullImage
import es.jvbabi.docker.kt.docker.DockerClient

class ImageApi internal constructor(private val client: DockerClient) {
    @Suppress("unused")
    suspend fun getImages(): List<DockerImage> = getImages(client)

    @Suppress("unused")
    /**
     * @throws RegistryNotAuthorizedException if the image is from a registry that requires authentication
     */
    suspend fun pull(
        image: String,
        beforeDownload: (layerHashes: List<String>) -> Unit = {},
        onDownload: (layerHash: String, status: ImagePullStatus) -> Unit,
        debugLogs: Boolean = false
    ) = pullImage(client, image, beforeDownload, onDownload, debugLogs)

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
        /**
         * Splits a reference into its registry and the rest.
         *
         * Everything before the first slash is the registry only when it looks like a host: it
         * carries a dot, carries a port, or is exactly "localhost". Otherwise it is the first path
         * segment of a Docker Hub repository, as in "library/alpine".
         *
         * Splitting the registry off first is what keeps a registry port out of the tag: in
         * "localhost:5000/app" the only colon belongs to the host, not to a tag.
         */
        private fun splitRegistry(image: String): Pair<String?, String> {
            val slash = image.indexOf('/')
            if (slash < 0) return null to image

            val candidate = image.substring(0, slash)
            val isHost = '.' in candidate || ':' in candidate || candidate == "localhost"
            return if (isHost) candidate to image.substring(slash + 1) else null to image
        }

        /** The registry to authenticate against, defaulting to Docker Hub. */
        fun registryFromImage(image: String): String = splitRegistry(image).first ?: "docker.io"

        /**
         * The repository to pull, registry included and tag stripped - the form Docker's
         * `fromImage` parameter expects.
         */
        fun repositoryFromImage(image: String): String {
            val (registry, remainder) = splitRegistry(image)
            val path = remainder.substringBefore(":")

            return when {
                // A registry-qualified reference names its repository in full already.
                registry != null -> "$registry/$path"
                // Official Docker Hub images live under the implicit "library" namespace.
                '/' !in path -> "library/$path"
                else -> path
            }
        }

        /** The tag to pull, defaulting to "latest". */
        fun tagFromImage(image: String): String =
            splitRegistry(image).second.substringAfter(":", "latest")
    }
}

class RegistryNotAuthorizedException(val registry: String): Exception("Not authorized for registry $registry")