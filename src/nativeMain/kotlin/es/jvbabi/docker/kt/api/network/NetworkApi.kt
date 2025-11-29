package es.jvbabi.docker.kt.api.network

import es.jvbabi.docker.kt.api.network.api.Network
import es.jvbabi.docker.kt.api.network.functions.internalCreateNetworkRequest
import es.jvbabi.docker.kt.api.network.functions.internalGetNetworksRequest
import es.jvbabi.docker.kt.docker.DockerClient

class NetworkApi internal constructor(private val client: DockerClient) {

    /**
     * @param internal If set to true, this network won't be able to connect to the outside world.
     */
    suspend fun createNetwork(
        name: String,
        driver: NetworkDriver = NetworkDriver.Bridge,
        scope: NetworkScope = NetworkScope.Local,
        ipamConfigs: List<IpamConfig>? = null,
        internal: Boolean = false,
        attachable: Boolean = true,
        enableIPv4: Boolean = true,
        enableIPv6: Boolean = true,
        labels: Map<String, String> = emptyMap()
    ) {
        internalCreateNetworkRequest(
            dockerClient = client,
            name = name,
            driver = driver,
            scope = scope,
            ipamConfigs = ipamConfigs,
            internal = internal,
            attachable = attachable,
            enableIPv4 = enableIPv4,
            enableIPv6 = enableIPv6,
            labels = labels,
        )
    }

    suspend fun getNetworks(): List<Network> =
        internalGetNetworksRequest(client)
}
