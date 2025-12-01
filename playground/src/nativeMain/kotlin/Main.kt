import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        DockerClient().use { dockerClient ->
            println(dockerClient.getInfo())
            val networks = dockerClient.networks.getNetworks()
            val testId = networks.find { it.name == "testnetwork" }?.id
            if (testId != null) dockerClient.networks.removeNetwork(testId)
        }
    }
}