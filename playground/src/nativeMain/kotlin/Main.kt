import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        DockerClient().use { dockerClient ->
            println("=== Docker Info ===")
            println(dockerClient.getInfo())

            println("\n=== Networks ===")
            val networks = dockerClient.networks.getNetworks()
            val testId = networks.find { it.name == "testnetwork" }?.id
            if (testId != null) dockerClient.networks.removeNetwork(testId)

            println("\n=== Containers ===")
            val containers = dockerClient.containers.getContainers(all = true)
            println("Total containers: ${containers.size}")
            containers.forEach { container ->
                println("  - ${container.names.firstOrNull()} | ${container.state} | ${container.image}")
            }
        }
    }
}