package es.jvbabi.docker.kt.api

import es.jvbabi.docker.kt.api.network.functions.internalCreateNetworkRequest
import es.jvbabi.docker.kt.api.network.functions.internalDeleteNetworkRequest
import es.jvbabi.docker.kt.docker.DockerClient
import kotlin.time.Instant

class Network internal constructor(
    internal val client: DockerClient,
    val name: String,
    val driver: Driver,
    val scope: Scope,
    val internal: Boolean,
    val attachable: Boolean,
    val enableIpv4: Boolean,
    val enableIpv6: Boolean,
    val ipamConfigs: List<IpamConfig>,
    val labels: Map<String, String>
) {

    lateinit var state: State

    /**
     * The id the daemon assigned. Only readable once the network exists, so guard on [state] before
     * reaching for it.
     */
    lateinit var id: String
        internal set

    /**
     * When the daemon created this network. Null for a draft, which does not exist yet.
     */
    var created: Instant? = null
        internal set

    sealed class State {
        data object Draft : State()
        data object Created : State()
        data object Deleted : State()
    }

    /**
     * Creates this network on the daemon.
     *
     * Only a [State.Draft] can be created - creating an existing network again would fail on the
     * duplicate name, and a deleted one may since have been recreated by someone else.
     */
    suspend fun create() {
        check(state is State.Draft) {
            "Only a draft can be created, but this network is $state"
        }

        id = internalCreateNetworkRequest(
            dockerClient = client,
            name = name,
            driver = driver,
            scope = scope,
            // No IPAM config at all is not the same as an empty one: only leaving it unset lets the
            // daemon pick a subnet itself, so an empty list must not be sent.
            ipamConfigs = ipamConfigs.ifEmpty { null },
            internal = internal,
            attachable = attachable,
            enableIPv4 = enableIpv4,
            enableIPv6 = enableIpv6,
            labels = labels
        )

        state = State.Created
    }

    /**
     * Removes this network from the daemon.
     *
     * Only a [State.Created] network can be removed - a draft was never sent anywhere, and a
     * deleted one no longer owns its id.
     */
    suspend fun remove() {
        check(state is State.Created) {
            "Only an existing network can be removed, but this network is $state"
        }

        internalDeleteNetworkRequest(client, id)

        state = State.Deleted
    }

    class Builder internal constructor(val client: DockerClient, val name: String) {
        var driver: Driver = Driver.Bridge
        var scope: Scope = Scope.Local
        var internal: Boolean = false
        var attachable: Boolean = true
        var enableIpv4: Boolean = true
        var enableIpv6: Boolean = true
        private val labelsMap: MutableMap<String, String> = mutableMapOf()
        private val ipamConfigs = mutableListOf<IpamConfig>()

        fun ipam(block: MutableList<IpamConfig>.() -> Unit) {
            ipamConfigs.apply(block)
        }

        fun labels(block: MutableMap<String, String>.() -> Unit) {
            labelsMap.apply(block)
        }

        /**
         * Assembles a [Network] that only exists locally: nothing has been sent to the daemon yet,
         * so it starts out as [State.Draft].
         *
         * The collected configuration is copied, so building twice - or mutating this builder
         * afterwards - cannot change a network that was already handed out.
         */
        internal fun build(): Network = Network(
            client = client,
            name = name,
            driver = driver,
            scope = scope,
            internal = internal,
            attachable = attachable,
            enableIpv4 = enableIpv4,
            enableIpv6 = enableIpv6,
            ipamConfigs = ipamConfigs.toList(),
            labels = labelsMap.toMap()
        ).apply {
            state = State.Draft
        }
    }

    enum class Driver {
        Bridge,
        Host,
        Overlay,
        Null
    }

    enum class Scope {
        Local,
        Swarm,
    }

    data class IpamConfig(
        val subnet: String,
        /** Docker leaves this unset unless the subnet is meant to be handed out only in part. */
        val ipRange: String? = null,
        val gateway: String,
        val auxAddress: Map<String, String> = emptyMap()
    )
}