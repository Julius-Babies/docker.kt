package es.jvbabi.docker.kt.docker.auth

import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import es.jvbabi.docker.kt.docker.config.DockerConfig
import es.jvbabi.docker.kt.docker.config.getDockerConfig
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

private val json by lazy { Json {
    ignoreUnknownKeys = true
    isLenient = true
} }

fun getAuthForRegistry(registry: String): String? {
    val dockerConfig = getDockerConfig() ?: return null
    when (dockerConfig.credsStore) {
        null -> {
            val auth = dockerConfig.auths?.get(registry) ?: return null
            return auth.auth
        }
        DockerConfig.CredsStore.Desktop -> return defaultInternalCredentialStore("docker-credential-desktop", registry)
        DockerConfig.CredsStore.OSXKeychain -> return defaultInternalCredentialStore("docker-credential-osxkeychain", registry)
    }
}

private val noCredentialsFound = listOf(
    "credentials not found in native keychain"
)

private fun defaultInternalCredentialStore(command: String, registry: String): String? {
    val command = Command(command)
        .args("get")
        .stdin(Stdio.Pipe)
        .stdout(Stdio.Pipe)
        .stderr(Stdio.Pipe)
        .spawn()
    val stdin = command.bufferedStdin()!!
    stdin.writeLine(registry)
    val out = command.waitWithOutput()
    val authString = out.stdout!!
        .lines()
        .firstOrNull() ?: return null
    if (authString.lowercase() in noCredentialsFound) return null
    val authResponseObject = json.decodeFromString<DockerCredentialDesktopResponse>(authString)
    return json.encodeToString<Auth>(Auth(authResponseObject.username, authResponseObject.secret))
        .let { Base64.encode(it.toByteArray()) }
}

@Serializable
data class DockerCredentialDesktopResponse(
    @SerialName("Username") val username: String,
    @SerialName("Secret") val secret: String
)

@Serializable
data class Auth(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)