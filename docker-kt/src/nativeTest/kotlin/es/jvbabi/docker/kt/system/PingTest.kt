package es.jvbabi.docker.kt.system

import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.withDocker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Covers `DockerClient.ping`, the cheapest way to find out whether the daemon is reachable at all.
 * Touches nothing on the host, so there is nothing to clean up.
 */
class PingTest : FunSpec({

    tags(RequiresDocker)

    test("ping reports a reachable daemon") {
        withDocker { client ->
            client.ping() shouldBe true
        }
    }

    test("ping can be repeated on the same client") {
        // The client talks over a single Unix socket, so a second request has to work just as well
        // as the first one.
        withDocker { client ->
            repeat(3) { client.ping() shouldBe true }
        }
    }
})
