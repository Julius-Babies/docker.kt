package es.jvbabi.docker.kt.docker.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerConfig(
    @SerialName("credsStore") val credsStore: CredsStore? = null,
    @SerialName("auths") val auths: Map<String, Auth>? = null
) {
    @Serializable
    enum class CredsStore {
        @SerialName("desktop") Desktop,
        @SerialName("osxkeychain") OSXKeychain,
    }

    @Serializable
    data class Auth(
        @SerialName("auth") val auth: String? = null
    )
}
