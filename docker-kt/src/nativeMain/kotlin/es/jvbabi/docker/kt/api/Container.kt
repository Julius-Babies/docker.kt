package es.jvbabi.docker.kt.api

import es.jvbabi.docker.kt.api.container.functions.createContainerInternal
import es.jvbabi.docker.kt.api.container.functions.deleteContainer
import es.jvbabi.docker.kt.docker.DockerClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Container internal constructor(
    internal val client: DockerClient,
    val image: String,
    val name: String?,
    val healthCheck: Healthcheck?,
    val entrypoint: List<String>?,
    val cmd: List<String>?,
    val volumes: List<VolumeBind>,
    val environment: Map<String, String>,
    val labels: Map<String, String>,
    val ports: List<PortBinding>,
    val exposedPorts: Map<Int, Set<PortBinding.Protocol>>,
    val networks: List<NetworkConfig>
) {
    lateinit var state: State

    /**
     * The id the daemon assigned. Only readable once the container exists, so guard on [state]
     * before reaching for it.
     */
    lateinit var id: String
        internal set

    sealed class State {
        sealed class NonExisting : State() {
            data object Draft : NonExisting()
            data object Deleted : NonExisting()
        }

        sealed class Existing : State() {
            data object Created : Existing()
            data object Running : Existing()
            data object Paused : Existing()
            data object Stopped : Existing()
        }
    }

    /**
     * Creates this container on the daemon.
     *
     * Only a [State.NonExisting.Draft] can be created - a container that already exists would be
     * duplicated, and a deleted one has a name the daemon may since have handed to someone else.
     * Every network it is attached to has to exist by now, since the container is registered with
     * their ids.
     *
     * @throws es.jvbabi.docker.kt.api.image.ImageNotFoundException if [image] does not exist
     */
    suspend fun create() {
        check(state is State.NonExisting.Draft) {
            "Only a draft can be created, but this container is $state"
        }

        networks.forEach { config ->
            check(config.network.state is Network.State.Created) {
                "Cannot attach to network '${config.network.name}': it is ${config.network.state}"
            }
        }

        id = createContainerInternal(
            dockerClient = client,
            image = image,
            name = name,
            healthCheck = healthCheck,
            volumeBinds = volumes,
            environment = environment,
            labels = labels,
            ports = ports,
            exposedPorts = exposedPorts,
            networkConfigs = networks,
            entrypoint = entrypoint,
            cmd = cmd
        )

        state = State.Existing.Created
    }

    /**
     * Removes this container from the daemon.
     *
     * Only an existing container can be removed - a draft was never sent anywhere, and a deleted one
     * no longer owns its id. Note that Docker refuses to remove a container that is still running.
     */
    suspend fun remove() {
        check(state is State.Existing) {
            "Only an existing container can be removed, but this container is $state"
        }

        deleteContainer(client, id)

        state = State.NonExisting.Deleted
    }

    class Builder internal constructor(val client: DockerClient, val image: String) {
        var name: String? = null
        var healthCheck: Healthcheck? = null
        var entrypoint: List<String>? = null
        var cmd: List<String>? = null
        private val volumeConfig = VolumeConfig()
        private val environment = mutableMapOf<String, String>()
        private val labels = mutableMapOf<String, String>()
        private val ports = PortConfig()
        private val networkConfig = NetworkConfig()

        fun volumes(block: VolumeConfig.() -> Unit) {
            volumeConfig.apply(block)
        }

        fun environment(block: MutableMap<String, String>.() -> Unit) {
            environment.apply(block)
        }

        fun labels(block: MutableMap<String, String>.() -> Unit) {
            labels.apply(block)
        }

        fun ports(block: PortConfig.() -> Unit) {
            ports.apply(block)
        }

        fun networks(block: NetworkConfig.() -> Unit) {
            networkConfig.apply(block)
        }

        class VolumeConfig {
            internal val volumes = mutableListOf<VolumeBind>()

            fun bindHost(path: String, containerPath: String, readOnly: Boolean = false) {
                this.volumes.add(VolumeBind.Host(path, containerPath, readOnly))
            }

            fun bindVolume(path: String, containerPath: String, readOnly: Boolean = false) {
                this.volumes.add(VolumeBind.Volume(path, containerPath, readOnly))
            }
        }

        class PortConfig {
            internal val ports = mutableListOf<PortBinding>()
            internal val exposedPorts = mutableMapOf<Int, Set<PortBinding.Protocol>>()

            /**
             * By default, each port you bind is added to the exposition metadata. This can be disabled by setting this
             * flag to false.
             */
            var disableAutomaticPortExposition: Boolean = false

            fun bind(containerPort: Int, hostPort: Int, protocols: Set<PortBinding.Protocol> = setOf(PortBinding.Protocol.TCP, PortBinding.Protocol.UDP)) {
                if (ports.any { portBinding -> portBinding.containerPort == containerPort && portBinding.protocol in protocols }) {
                    throw IllegalArgumentException("Container port $containerPort is already bound to a host port.")
                }
                protocols.forEach { protocol ->
                    ports.add(PortBinding(hostPort, containerPort, protocol))
                }
            }

            fun expose(containerPort: Int, protocols: Set<PortBinding.Protocol> = setOf(PortBinding.Protocol.TCP, PortBinding.Protocol.UDP)) {
                exposedPorts[containerPort] = protocols
            }
        }

        class NetworkConfig {
            internal val networks = mutableListOf<Container.NetworkConfig>()

            /**
             * Attaches the container to [network]. The network has to exist by the time the
             * container is created - see [Container.create].
             */
            fun connect(network: Network, containerAliases: List<String> = emptyList()) {
                networks.add(Container.NetworkConfig(network, containerAliases))
            }
        }

        /**
         * The exposition metadata the container is created with.
         *
         * Unless [PortConfig.disableAutomaticPortExposition] is set, every bound port is exposed as
         * well, on the protocols it was bound with. An explicit [PortConfig.expose] for the same
         * container port adds to that rather than replacing it.
         */
        private fun exposedPorts(): Map<Int, Set<PortBinding.Protocol>> {
            val fromBindings = if (ports.disableAutomaticPortExposition) {
                emptyMap()
            } else {
                ports.ports
                    .groupBy({ it.containerPort }, { it.protocol })
                    .mapValues { (_, protocols) -> protocols.toSet() }
            }

            return (fromBindings.keys + ports.exposedPorts.keys).associateWith { containerPort ->
                fromBindings[containerPort].orEmpty() + ports.exposedPorts[containerPort].orEmpty()
            }
        }

        /**
         * Assembles a [Container] that only exists locally: nothing has been sent to the daemon yet,
         * so it starts out as [State.NonExisting.Draft].
         *
         * The collected configuration is copied, so building twice - or mutating this builder
         * afterwards - cannot change a container that was already handed out.
         */
        internal fun build(): Container = Container(
            client = client,
            image = image,
            name = name,
            healthCheck = healthCheck,
            entrypoint = entrypoint,
            cmd = cmd,
            volumes = volumeConfig.volumes.toList(),
            environment = environment.toMap(),
            labels = labels.toMap(),
            ports = ports.ports.toList(),
            exposedPorts = exposedPorts(),
            networks = networkConfig.networks.toList()
        ).apply {
            state = State.NonExisting.Draft
        }
    }

    data class Healthcheck(
        val test: List<String>,
        val interval: Duration = 30.seconds,
        val timeout: Duration = 30.seconds,
        val startPeriod: Duration = 0.seconds,
        val retries: Int = 3
    )

    sealed class VolumeBind {
        abstract val readOnly: Boolean
        abstract val containerPath: String

        data class Host(
            val path: String,
            override val containerPath: String,
            override val readOnly: Boolean = false
        ) : VolumeBind()

        data class Volume(
            val name: String,
            override val containerPath: String,
            override val readOnly: Boolean = false
        ) : VolumeBind()

        companion object {
            /**
             * Parses a bind in Docker CLI notation: `source:/container/path`, optionally followed by
             * `:ro` or `:rw`.
             *
             * A source starting with `/` or `.` is a path on the host, anything else names a volume.
             */
            fun from(input: String): VolumeBind = when (input.count { it == ':' }) {
                1 -> {
                    val (source, containerPath) = input.split(":")
                    of(source, containerPath, readOnly = false)
                }

                2 -> {
                    val (source, containerPath, mode) = input.split(":")
                    of(source, containerPath, readOnly = mode.lowercase() == "ro")
                }

                else -> error("Invalid volume bind: $input")
            }

            private fun of(source: String, containerPath: String, readOnly: Boolean): VolumeBind =
                if (source.startsWith("/") || source.startsWith(".")) {
                    Host(source, containerPath, readOnly)
                } else {
                    Volume(source, containerPath, readOnly)
                }
        }
    }

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
        val network: Network,
        val aliases: List<String> = emptyList()
    )
}