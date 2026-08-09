package es.jvbabi.docker.kt.api.network

import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.api.network.functions.internalGetNetworkByIdRequest
import es.jvbabi.docker.kt.api.network.functions.internalGetNetworksRequest
import es.jvbabi.docker.kt.docker.DockerClient

class NetworkApi internal constructor(private val client: DockerClient) {

    /**
     * Every network the daemon knows, each one ready to work with: id filled in and state
     * [Network.State.Created], so [Network.remove] works on it right away.
     */
    suspend fun getNetworks(): List<Network> =
        internalGetNetworksRequest(client)

    /**
     * Looks up a single network, the same way [getNetworks] hands them out.
     *
     * @return null if the daemon does not know that id
     */
    suspend fun getById(id: String): Network? =
        internalGetNetworkByIdRequest(client, id)
}
