package es.jvbabi.docker.kt.api.network

data class IpamConfig(
    val subnet: String,
    val ipRange: String,
    val gateway: String,
    val auxAddress: Map<String, String> = emptyMap()
)