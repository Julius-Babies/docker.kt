import es.jvbabi.docker.kt.docker.DockerClient
import io.kotest.core.spec.style.FunSpec

class GetImagesTest : FunSpec({
    test("List images") {
        DockerClient().use { client ->
            client.images.getImages().forEach { println(it) }
        }
    }
})