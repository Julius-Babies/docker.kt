package es.jvbabi.docker.kt.api.container.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Inspect(
    @SerialName("Id") val id: String,
    @SerialName("Names") val names: List<String>? = null,
    @SerialName("Image") val image: String,
    @SerialName("State") val state: ContainerState,
    @SerialName("Config") val config: ContainerConfig,
    @SerialName("HostConfig") val hostConfig: HostConfig,
)

@Serializable
data class ContainerState(
    @SerialName("Status") val status: String,
    @SerialName("Running") val running: Boolean,
    @SerialName("Paused") val paused: Boolean,
    @SerialName("Restarting") val restarting: Boolean,
    @SerialName("OOMKilled") val oomKilled: Boolean,
    @SerialName("Dead") val dead: Boolean,
    @SerialName("ExitCode") val exitCode: Int,
    @SerialName("Error") val error: String? = null,
    @SerialName("StartedAt") val startedAt: String,
    @SerialName("FinishedAt") val finishedAt: String,
    @SerialName("Health") val health: Health? = null
)

@Serializable
data class Health(
    @SerialName("Status") val status: String,
    @SerialName("FailingStreak") val failingStreak: Int,
    @SerialName("Log") val log: List<HealthLog>
)

@Serializable
data class HealthLog(
    @SerialName("Start") val start: String,
    @SerialName("End") val end: String,
    @SerialName("ExitCode") val exitCode: Int,
    @SerialName("Output") val output: String
)

@Serializable
data class ContainerConfig(
    @SerialName("Hostname") val hostname: String,
    @SerialName("Domainname") val domainname: String,
    @SerialName("User") val user: String,
    @SerialName("AttachStdin") val attachStdin: Boolean,
    @SerialName("AttachStdout") val attachStdout: Boolean,
    @SerialName("AttachStderr") val attachStderr: Boolean,
    @SerialName("ExposedPorts") val exposedPorts: Map<String, Map<String, String>>,
    @SerialName("Tty") val tty: Boolean,
    @SerialName("OpenStdin") val openStdin: Boolean,
    @SerialName("StdinOnce") val stdinOnce: Boolean,
    @SerialName("Env") val env: List<String>,
    @SerialName("Cmd") val cmd: List<String>,
    @SerialName("Image") val image: String,
    @SerialName("Labels") val labels: Map<String, String>,
    @SerialName("WorkingDir") val workingDir: String,
    @SerialName("NetworkMode") val networkMode: String? = null
)

@Serializable
data class HostConfig(
    @SerialName("NetworkMode") val networkMode: String,
    @SerialName("PortBindings") val portBindings: Map<String, List<PortBinding>>
)

@Serializable
data class PortBinding(
    @SerialName("HostIp") val hostIp: String,
    @SerialName("HostPort") val hostPort: String
)
