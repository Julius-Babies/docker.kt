import docker.getHttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Hello, Kotlin/Native!")
    val client = getHttpClient()
    val dockerInfoResponse = client.get("/info")

    println("Docker Info Response: ${dockerInfoResponse.bodyAsText()}")
}
