package es.jvbabi.docker.kt.api.image

sealed class ImagePullStatus {
    data class Pulling(val bytesTotal: Long, val bytesPulled: Long): ImagePullStatus()
    data class Extracting(val layerHash: String, val current: Long, val unit: String): ImagePullStatus()
    data object Downloaded: ImagePullStatus()
}