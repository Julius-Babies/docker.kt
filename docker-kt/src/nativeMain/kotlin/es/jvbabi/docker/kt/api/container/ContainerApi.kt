package es.jvbabi.docker.kt.api.container

import es.jvbabi.docker.kt.api.container.api.DockerContainer
import es.jvbabi.docker.kt.api.container.functions.*
import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

class ContainerApi internal constructor(private val client: DockerClient) {
    /**
     * Lists all containers on the Docker host.
     *
     * @param all If true, returns all containers (including stopped ones).
     *            If false, it returns only running containers. Default is false.
     * @return A list of [DockerContainer] objects representing the containers.
     */
    suspend fun getContainers(all: Boolean = false): List<DockerContainer> = getContainers(client, all)

    /**
     * Creates a new container.
     *
     * @param image The image to use for the container (e.g., "nginx:latest")
     * @param name Optional name for the container
     * @param volumeBinds Map of volume bindings: [VolumeBind] to container path
     * @param environment Map of environment variables: key to value
     * @param labels Map of labels: key to value
     * @param exposedPorts List of ports to expose without host binding
     * @throws es.jvbabi.docker.kt.api.image.ImageNotFoundException if the specified image does not exist
     */
    suspend fun createContainer(
        image: String,
        name: String? = null,
        volumeBinds: Map<VolumeBind, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        labels: Map<String, String> = emptyMap(),
        ports: List<PortBinding> = emptyList(),
        exposedPorts: Map<Int, PortBinding.Protocol> = emptyMap(),
        networkConfigs: List<NetworkConfig> = emptyList()
    ) = createContainerInternal(
        dockerClient = client,
        image = image,
        name = name,
        volumeBinds = volumeBinds,
        environment = environment,
        labels = labels,
        ports = ports,
        exposedPorts = exposedPorts,
        networkConfigs = networkConfigs,
    )

    /**
     * Starts a container.
     * @param containerId The ID of the container to start
     * @param exceptionOnAlreadyRunning If true, throws an exception if the container is already running
     * @throws ContainerAlreadyRunningException if the container is already running and if [exceptionOnAlreadyRunning] is true
     */
    suspend fun startContainer(containerId: String, exceptionOnAlreadyRunning: Boolean = false) =
        startContainerInternal(client, containerId, exceptionOnAlreadyRunning)

    suspend fun stopContainer(id: String) = stopContainer(client, id)

    suspend fun restartContainer(id: String) = restartContainer(client, id)

    suspend fun killContainer(id: String) = killContainer(client, id)

    suspend fun pauseContainer(id: String) = pauseContainer(client, id)

    suspend fun deleteContainer(id: String) = deleteContainer(client, id)

    suspend fun runCommand(
        containerId: String,
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): CommandResult =
        runCommandInternalSimple(
            dockerClient = client,
            containerId = containerId,
            command = command,
            environment = environment,
        )

    /**
     * Asynchronous variant of `runCommand` that returns streaming flows for stdout and stderr
     * as well as a Deferred that completes with the exit code once the command finished.
     *
     * The function returns immediately; the streams produce strings as data arrives.
     */
    fun runCommandStream(
        containerId: String,
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): CommandStreamResult =
        runCommandInternalFlow(
            dockerClient = client,
            containerId = containerId,
            command = command,
            environment = environment,
        )
}

data class CommandResult(val exitCode: Int, val output: String)

/**
 * Result of a streaming command execution.
 * stdout/stderr are cold Flows backed by channels; exitCode is completed when the command finishes.
 */
data class CommandStreamResult(
    val stdout: Flow<String>,
    val stderr: Flow<String>,
    val exitCode: Deferred<Int>
)

sealed class VolumeBind {
    abstract val readOnly: Boolean

    data class Host(
        val path: String,
        override val readOnly: Boolean = false
    ) : VolumeBind()

    data class Volume(
        val name: String,
        override val readOnly: Boolean = false
    ) : VolumeBind()

    companion object {
        fun from(input: String): Pair<VolumeBind, String> {
            when (input.count { it == ':' }) {
                1 -> {
                    val (host, container) = input.split(":")
                    return Host(host, false) to container
                }
                2 -> {
                    val (host, container, readOnly) = input.split(":")
                    return Host(host, readOnly.lowercase() == "ro") to container
                }
                else -> error("Invalid volume bind: $input")
            }
        }
    }
}

data class NetworkConfig(
    val networkId: String,
    val aliases: List<String> = emptyList()
)

data class PortBinding(
    val hostPort: Int,
    val containerPort: Int,
    val protocol: Protocol
) {
    enum class Protocol { TCP, UDP }
}

class ContainerAlreadyRunningException(val id: String): Exception("Container $id is already running")