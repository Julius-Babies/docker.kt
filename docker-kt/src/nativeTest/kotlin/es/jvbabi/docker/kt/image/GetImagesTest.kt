package es.jvbabi.docker.kt.image

import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import io.kotest.core.spec.style.FunSpec

class GetImagesTest : FunSpec({
    tags(RequiresDocker)

    test("List images") {
        DockerClient().use { client ->
            client.images.getImages().forEach { println(it) }
        }
    }
})