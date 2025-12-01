import es.jvbabi.docker.kt.docker.DockerClient
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        DockerClient().use { dockerClient ->
            println(dockerClient.getInfo())
        }
    }
}