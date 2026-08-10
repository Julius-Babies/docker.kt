package es.jvbabi.docker.kt.api.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.dto.DockerContainer
import es.jvbabi.docker.kt.dto.Inspect
import es.jvbabi.docker.kt.api.container.functions.*
import es.jvbabi.docker.kt.api.network.functions.internalGetNetworksRequest
import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class ContainerApi internal constructor(private val client: DockerClient) {
    /**
     * Lists the containers on the Docker host, each one ready to work with: id filled in and state
     * read from the daemon, so [Container.remove] works on them right away.
     *
     * The listing itself is only a summary - it carries no environment, entrypoint or healthcheck -
     * so every container is inspected on top of it rather than handed back half filled in. That is
     * one request per container; [getById] is the cheaper way in when the id is already known.
     *
     * @param all If true, returns all containers (including stopped ones).
     *            If false, it returns only running containers. Default is false.
     */
    suspend fun getContainers(all: Boolean = false): List<Container> {
        val listed = getContainers(client, all)
        if (listed.isEmpty()) return emptyList()

        // Shared across the whole batch instead of being looked up per container.
        val knownNetworks = internalGetNetworksRequest(client)

        return listed.mapNotNull { summary ->
            // A container can be gone between the listing and the inspect.
            inspectContainerOrNull(client, summary.id)?.toContainer(knownNetworks)
        }
    }

    /**
     * Looks up an existing container and hands it back as a [Container] that can be worked with -
     * its id filled in and its state read from the daemon, so [Container.remove] works on it
     * right away.
     *
     * @return null if the daemon does not know that id
     */
    suspend fun getById(id: String): Container? {
        val inspect = inspectContainerOrNull(client, id) ?: return null
        return inspect.toContainer(networksFor(inspect))
    }

    private fun Inspect.toContainer(knownNetworks: List<Network>): Container {
        val inspect = this

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
            networks = resolveNetworks(inspect, knownNetworks)
        ).apply {
            this.id = inspect.id
            state = inspect.state.status.toContainerState()
        }
    }

    /** The networks needed to resolve this one container's endpoints, or none if it has none. */
    private suspend fun networksFor(inspect: Inspect): List<Network> =
        if (inspect.networkSettings.networks.isEmpty()) emptyList()
        else internalGetNetworksRequest(client)

    /**
     * Turns the endpoints inspect reports into the [Network] objects a [Container] holds on to.
     *
     * Endpoints only carry an id, so they are matched against [knownNetworks] rather than looked up
     * one by one. Which key identifies them depends on how far the container got: once it has run,
     * inspect keys the endpoints by network name and fills in NetworkID - before that the key is
     * whatever the create request used, and NetworkID is still empty.
     */
    private fun resolveNetworks(
        inspect: Inspect,
        knownNetworks: List<Network>
    ): List<Container.NetworkConfig> =
        inspect.networkSettings.networks.mapNotNull { (key, endpoint) ->
            val network = knownNetworks.find { it.id == endpoint.networkId }
                ?: knownNetworks.find { it.id == key || it.name == key }
                // A network the daemon no longer lists cannot be handed out as an object. It cannot
                // normally happen either: Docker refuses to remove a network still in use.
                ?: return@mapNotNull null

            Container.NetworkConfig(network = network, aliases = endpoint.aliases.orEmpty())
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

    suspend fun restartContainer(id: String) = restartContainer(client, id)

    suspend fun deleteContainer(id: String) = deleteContainer(client, id)

    /** Not public: [Inspect] is a wire type. Use [getById] for a container to work with. */
    internal suspend fun inspectContainer(id: String) = inspectContainer(client, id)

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