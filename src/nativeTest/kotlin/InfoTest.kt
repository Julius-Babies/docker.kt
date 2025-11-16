import es.jvbabi.docker.kt.docker.DockerClient
import io.kotest.core.spec.style.FunSpec

class InfoTest: FunSpec({
    test("info") {
        DockerClient().use { client ->
            println(client.getInfo())
        }
    }
})