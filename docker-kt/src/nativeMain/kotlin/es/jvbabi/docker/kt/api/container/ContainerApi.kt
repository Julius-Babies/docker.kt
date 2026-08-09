package es.jvbabi.docker.kt.api.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.container.api.DockerContainer
import es.jvbabi.docker.kt.api.container.functions.*
import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

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
     * Looks up an existing container and hands it back as a [Container] that can be worked with -
     * its id filled in and its state read from the daemon, so [Container.remove] works on it
     * right away.
     *
     * @return null if the daemon does not know that id
     */
    suspend fun getById(id: String): Container? {
        val inspect = inspectContainerOrNull(client, id) ?: return null

        return Container(
            client = client,
            image = inspect.config.image,
            // Inspect reports the name with a leading slash, the builder takes it without.
            name = inspect.name?.trimStart('/'),
            healthCheck = inspect.config.healthcheck?.let { healthcheck ->
                Container.Healthcheck(
                    test = healthcheck.test,
                    interval = healthcheck.interval.nanoseconds,
                    timeout = healthcheck.timeout.nanoseconds,
                    startPeriod = healthcheck.startPeriod.nanoseconds,
                    retries = healthcheck.retries
                )
            },
            entrypoint = inspect.config.entrypoint,
            cmd = inspect.config.cmd,
            volumes = inspect.mounts.map { mount ->
                if (mount.type == "volume") {
                    Container.VolumeBind.Volume(
                        name = mount.name.orEmpty(),
                        containerPath = mount.destination,
                        readOnly = !mount.rw
                    )
                } else {
                    Container.VolumeBind.Host(
                        path = mount.source,
                        containerPath = mount.destination,
                        readOnly = !mount.rw
                    )
                }
            },
            environment = inspect.config.env.associate { entry ->
                // Values may contain '=' themselves, so only split on the first one.
                entry.substringBefore('=') to entry.substringAfter('=', "")
            },
            labels = inspect.config.labels,
            ports = inspect.hostConfig.portBindings.flatMap { (port, bindings) ->
                val (containerPort, protocol) = port.toPortAndProtocol()
                bindings.map { binding ->
                    Container.PortBinding(
                        hostPort = binding.hostPort.toInt(),
                        containerPort = containerPort,
                        protocol = protocol
                    )
                }
            },
            exposedPorts = inspect.config.exposedPorts.keys
                .map { it.toPortAndProtocol() }
                .groupBy({ (port, _) -> port }, { (_, protocol) -> protocol })
                .mapValues { (_, protocols) -> protocols.toSet() },
            networks = inspect.networkSettings.networks.map { (_, endpoint) ->
                Container.NetworkConfig(
                    networkId = endpoint.networkId,
                    aliases = endpoint.aliases.orEmpty()
                )
            }
        ).apply {
            this.id = inspect.id
            state = inspect.state.status.toContainerState()
        }
    }

    /** Parses Docker's `"80/tcp"` port notation. */
    private fun String.toPortAndProtocol(): Pair<Int, Container.PortBinding.Protocol> =
        substringBefore('/').toInt() to
            Container.PortBinding.Protocol.valueOf(substringAfter('/', "tcp").uppercase())

    /**
     * Maps the status Docker reports onto the states [Container] distinguishes. Docker knows a few
     * transient ones on top: a container on its way up counts as running, one on its way out or
     * already gone as stopped.
     */
    private fun String.toContainerState(): Container.State =
        when (ContainerState.fromString(this)) {
            ContainerState.CREATED -> Container.State.Existing.Created
            ContainerState.RUNNING, ContainerState.RESTARTING -> Container.State.Existing.Running
            ContainerState.PAUSED -> Container.State.Existing.Paused
            ContainerState.EXITED, ContainerState.DEAD, ContainerState.REMOVING ->
                Container.State.Existing.Stopped
            null -> throw RuntimeException("Unknown container state: $this")
        }

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

    suspend fun inspectContainer(id: String) = inspectContainer(client, id)

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

object Container {
    data class Healthcheck(
        val test: List<String>,
        val interval: Duration = 30.seconds,
        val timeout: Duration = 30.seconds,
        val startPeriod: Duration = 0.seconds,
        val retries: Int = 3
    )

    data class PortBinding(
        val hostPort: Int,
        val containerPort: Int,
        val protocol: Protocol
    ) {
        enum class Protocol { TCP, UDP }

        companion object {
            fun from(input: String): PortBinding {
                if ('/' in input) {
                    val (ports, protocol) = input.split("/")
                    return from(ports).copy(protocol = Protocol.valueOf(protocol.uppercase()))
                }
                val (hostPort, containerPort) = input.split(":")
                return PortBinding(
                    hostPort = hostPort.toInt(),
                    containerPort = containerPort.toInt(),
                    protocol = Protocol.TCP
                )
            }
        }
    }

    data class NetworkConfig(
        val networkId: String,
        val aliases: List<String> = emptyList()
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
}

class ContainerAlreadyRunningException(val id: String): Exception("Container $id is already running")