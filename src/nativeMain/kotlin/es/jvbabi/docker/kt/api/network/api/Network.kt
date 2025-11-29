package es.jvbabi.docker.kt.api.network.api

import es.jvbabi.docker.kt.api.network.NetworkDriver
import es.jvbabi.docker.kt.api.network.NetworkScope
import kotlin.time.Instant

data class Network(
    val name: String,
    val id: String,
    val created: Instant,
    val scope: NetworkScope,
    val driver: NetworkDriver,
    val enableIPv6: Boolean,
    val enableIPv4: Boolean,
    val internal: Boolean,
    val attachable: Boolean,
    val ipam: Ipam?,
    val labels: Map<String, String>
) {
    data class Ipam(
        val driver: String,
        val configs: List<IpamConfig>
    ) {
        data class IpamConfig(
            val subnet: String,
            val ipRange: String,
            val gateway: String,
            val auxAddress: Map<String, String>
        )
    }
}