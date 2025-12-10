import es.jvbabi.docker.kt.api.container.Container
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.docker.auth.getAuthForRegistry
import es.jvbabi.docker.kt.docker.getSocketPath
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

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
            dockerClient.images.pull("postgres:18.1-alpine3.22", onDownload = { _, _ -> })
            println("Creating test container...")
            var containerId = dockerClient.containers.getContainers(all = true)
                .firstOrNull { it.names.contains("/testcontainer") }?.id
            if (containerId == null) dockerClient.containers.createContainer(
                image = "postgres:18.1-alpine3.22",
                volumeBinds = mapOf(
                    Container.VolumeBind.Host(getSocketPath()) to "/var/run/docker.sock"
                ),
                name = "testcontainer",
                environment = mapOf("POSTGRES_PASSWORD" to "testpw")
            )
            containerId = dockerClient.containers.getContainers(all = true).firstOrNull { it.names.contains("/testcontainer") }?.id
            requireNotNull(containerId)
            coroutineScope {
                launch { dockerClient.containers.startContainer(containerId) }
                repeat(10) {
                    println(dockerClient.containers.inspectContainer(containerId).state.status)
                    delay(100.milliseconds)
                }
            }

            val r = dockerClient.containers.runCommand(containerId, listOf("ls", "-lah", "/"))
            println(r.output)

            val r2 = dockerClient.containers.runCommandStream(
                containerId = containerId,
                command = listOf("sh", "-c", "echo 'Hello from container!' && sleep 3 && echo 'Bye!'"),
            )
            r2.stdout.collect { println(it) }

            containers.forEach { container ->
                println("  - ${container.names.firstOrNull()} | ${container.state} | ${container.image}")
            }
        }
    }
}