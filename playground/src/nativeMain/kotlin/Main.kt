import es.jvbabi.docker.kt.api.container.VolumeBind
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.docker.auth.getAuthForRegistry
import es.jvbabi.docker.kt.docker.getSocketPath
import kotlinx.coroutines.runBlocking
import kotlin.native.internal.collectReferenceFieldValues

fun main() {
    runBlocking {
        DockerClient().use { dockerClient ->
            println("=== Docker Info ===")
            println(dockerClient.getInfo())

            println(getAuthForRegistry("registry.gitlab.jvbabi.es"))

            println("\n=== Networks ===")
            val networks = dockerClient.networks.getNetworks()
            val testId = networks.find { it.name == "testnetwork" }?.id
            if (testId != null) dockerClient.networks.removeNetwork(testId)

            println("\n=== Containers ===")
            val containers = dockerClient.containers.getContainers(all = true)
            println("Total containers: ${containers.size}")

            // Create test container
            dockerClient.images.pull("alpine:latest", onDownload = { _, _ -> })
            println("Creating test container...")
            var containerId = dockerClient.containers.getContainers(all = true)
                .firstOrNull { it.names.contains("/testcontainer") }?.id
            if (containerId == null) dockerClient.containers.createContainer(
                image = "alpine:latest",
                volumeBinds = mapOf(
                    VolumeBind.Host(getSocketPath()) to "/var/run/docker.sock"
                ),
                name = "testcontainer"
            )
            containerId = dockerClient.containers.getContainers(all = true).firstOrNull { it.names.contains("/testcontainer") }?.id
            // start test container
            dockerClient.containers.startContainer(containerId!!)


            containers.forEach { container ->
                println("  - ${container.names.firstOrNull()} | ${container.state} | ${container.image}")
            }
        }
    }
}