import docker.DockerClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Hello, Kotlin/Native!")
    val client = DockerClient()

    client.use { client ->
        println(client.getInfo())
    }
}
