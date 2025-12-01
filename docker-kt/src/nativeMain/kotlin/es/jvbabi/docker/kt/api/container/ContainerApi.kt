package es.jvbabi.docker.kt.api.container

import es.jvbabi.docker.kt.api.container.api.DockerContainer
import es.jvbabi.docker.kt.api.container.functions.createContainer
import es.jvbabi.docker.kt.api.container.functions.deleteContainer
import es.jvbabi.docker.kt.api.container.functions.getContainers
import es.jvbabi.docker.kt.api.container.functions.killContainer
import es.jvbabi.docker.kt.api.container.functions.pauseContainer
import es.jvbabi.docker.kt.api.container.functions.restartContainer
import es.jvbabi.docker.kt.api.container.functions.startContainer
import es.jvbabi.docker.kt.api.container.functions.stopContainer
import es.jvbabi.docker.kt.docker.DockerClient

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
     * @param ports Map of port mappings: container port to host port (e.g., 80 to 8080)
     * @param exposedPorts List of ports to expose without host binding
     * @throws es.jvbabi.docker.kt.api.image.ImageNotFoundException if the specified image does not exist
     */
    suspend fun createContainer(
        image: String,
        name: String? = null,
        volumeBinds: Map<VolumeBind, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        labels: Map<String, String> = emptyMap(),
        ports: Map<Int, Int> = emptyMap(),
        exposedPorts: List<Int> = emptyList()
    ) = createContainer(
        dockerClient = client,
        image = image,
        name = name,
        volumeBinds = volumeBinds,
        environment = environment,
        labels = labels,
        ports = ports,
        exposedPorts = exposedPorts
    )

    suspend fun startContainer(id: String) = startContainer(client, id)

    suspend fun stopContainer(id: String) = stopContainer(client, id)

    suspend fun restartContainer(id: String) = restartContainer(client, id)

    suspend fun killContainer(id: String) = killContainer(client, id)

    suspend fun pauseContainer(id: String) = pauseContainer(client, id)

    suspend fun deleteContainer(id: String) = deleteContainer(client, id)
}

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