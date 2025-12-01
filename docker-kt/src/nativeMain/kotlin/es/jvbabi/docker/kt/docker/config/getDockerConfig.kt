package es.jvbabi.docker.kt.docker.config

import es.jvbabi.kfile.File
import kotlinx.serialization.json.Json

private val json by lazy { Json {
    ignoreUnknownKeys = true
    isLenient = true
} }

fun getDockerConfig(): DockerConfig? {
    val userConfigFile = File
        .getUserHomeDirectory()
        .resolve(".docker")
        .resolve("config.json")
    if (!userConfigFile.exists()) return null
    val content = userConfigFile.readText()
    val config = json.decodeFromString<DockerConfig>(content)
    return config
}