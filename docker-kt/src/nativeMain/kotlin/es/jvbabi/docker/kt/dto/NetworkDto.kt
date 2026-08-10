package es.jvbabi.docker.kt.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DockerNetworksResponse(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Scope") val scope: String,
    @SerialName("Driver") val driver: String,
    @SerialName("EnableIPv6") val enableIPv6: Boolean,
    @SerialName("EnableIPv4") val enableIPv4: Boolean,
    @SerialName("Internal") val internal: Boolean,
    @SerialName("Attachable") val attachable: Boolean,
    @SerialName("IPAM") val ipam: Ipam,
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("Created") val createdAt: String
) {
    @Serializable
    data class Ipam(
        @SerialName("Driver") val driver: String,
        @SerialName("Config") val configs: List<IpamConfig>?
    ) {
        @Serializable
        data class IpamConfig(
            @SerialName("Subnet") val subnet: String,
            @SerialName("Gateway") val gateway: String,
            @SerialName("AuxiliaryAddresses") val auxiliaryAddresses: Map<String, String> = emptyMap(),
            @SerialName("IPRange") val ipRange: String? = null
        )
    }
}

@Serializable
internal data class CreateNetworkResponse(
    @SerialName("Id") val id: String,
    @SerialName("Warning") val warning: String? = null
)
