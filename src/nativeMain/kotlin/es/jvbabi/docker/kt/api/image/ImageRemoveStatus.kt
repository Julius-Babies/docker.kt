package es.jvbabi.docker.kt.api.image

data class ImageRemoveStatus(
    val id: String,
    val type: Type
) {
    enum class Type {
        Deleted,
        Untagged
    }
}