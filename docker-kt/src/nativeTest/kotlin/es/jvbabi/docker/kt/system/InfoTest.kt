package es.jvbabi.docker.kt.system

import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.withDocker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty

class InfoTest : FunSpec({

    tags(RequiresDocker)

    test("info describes the daemon") {
        withDocker { client ->
            val info = client.getInfo()

            info.id.shouldNotBeEmpty()
            info.serverVersion.shouldNotBeEmpty()
            info.operatingSystem.shouldNotBeEmpty()
            info.nCpu shouldBeGreaterThan 0
        }
    }

    test("info reads the daemon's warnings as the list they are") {
        withDocker { client ->
            // Docker sends Warnings as an array of strings - a single string here meant every
            // getInfo() on a host with warnings failed to deserialise.
            client.getInfo().warnings shouldNotBe null
        }
    }
})
