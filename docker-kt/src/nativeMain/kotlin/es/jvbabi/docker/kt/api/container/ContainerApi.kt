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
    suspend fun createContainer(
        image: String,
        name: String? = null,
        volumeBinds: Map<VolumeBind, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
        labels: Map<String, String> = emptyMap()
    ) = createContainer(
        dockerClient = client,
        image = image,
        name = name,
        volumeBinds = volumeBinds,
        environment = environment,
        labels = labels
    )

    suspend fun startContainer(id: String) = startContainer(client, id)

    suspend fun stopContainer(id: String) = stopContainer(client, id)

    suspend fun restartContainer(id: String) = restartContainer(client, id)

    suspend fun killContainer(id: String) = killContainer(client, id)

    suspend fun pauseContainer(id: String) = pauseContainer(client, id)

    suspend fun deleteContainer(id: String) = deleteContainer(client, id)
}

sealed class VolumeBind {
    data class Host(val path: String) : VolumeBind()
    data class Volume(val name: String): VolumeBind()
}