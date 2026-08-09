package es.jvbabi.docker.kt.system

import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import io.kotest.core.spec.style.FunSpec

class InfoTest: FunSpec({
    tags(RequiresDocker)

    test("info") {
        DockerClient().use { client ->
            println(client.getInfo())
        }
    }
})