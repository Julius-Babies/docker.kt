package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.container.Container
import es.jvbabi.docker.kt.api.container.ContainerState
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the parsing helpers around containers. Nothing here talks to a daemon, so the spec
 * carries no `RequiresDocker` tag and runs anywhere.
 */
class ContainerParsingTest : FunSpec({

    context("PortBinding.from") {
        test("defaults to TCP when the spec carries no protocol") {
            Container.PortBinding.from("8080:80") shouldBe Container.PortBinding(
                hostPort = 8080,
                containerPort = 80,
                protocol = Container.PortBinding.Protocol.TCP
            )
        }

        test("reads the protocol from the suffix") {
            Container.PortBinding.from("8080:80/udp").protocol shouldBe Container.PortBinding.Protocol.UDP
            Container.PortBinding.from("8080:80/tcp").protocol shouldBe Container.PortBinding.Protocol.TCP
        }

        test("keeps the ports apart when a protocol is given") {
            val binding = Container.PortBinding.from("53:5353/udp")
            binding.hostPort shouldBe 53
            binding.containerPort shouldBe 5353
        }

        test("rejects a spec without a container port") {
            shouldThrowAny { Container.PortBinding.from("8080") }
        }

        test("rejects a spec whose ports are not numbers") {
            shouldThrowAny { Container.PortBinding.from("http:https") }
        }
    }

    context("VolumeBind.from") {
        test("maps a host path onto a container path") {
            Container.VolumeBind.from("/srv/data:/app/data") shouldBe
                (Container.VolumeBind.Host("/srv/data") to "/app/data")
        }

        test("reads the read-only flag from the third segment") {
            Container.VolumeBind.from("/srv/data:/app/data:ro") shouldBe
                (Container.VolumeBind.Host("/srv/data", readOnly = true) to "/app/data")

            Container.VolumeBind.from("/srv/data:/app/data:rw") shouldBe
                (Container.VolumeBind.Host("/srv/data", readOnly = false) to "/app/data")
        }

        test("rejects a bind without a container path") {
            shouldThrowAny { Container.VolumeBind.from("/srv/data") }
        }

        test("rejects a bind with more segments than it knows") {
            shouldThrowAny { Container.VolumeBind.from("/srv/data:/app/data:ro:extra") }
        }
    }

    context("ContainerState.fromString") {
        test("maps every state it can report back onto itself") {
            ContainerState.entries.forEach { state ->
                ContainerState.fromString(state.value) shouldBe state
            }
        }

        test("ignores the case Docker happens to use") {
            ContainerState.fromString("RUNNING") shouldBe ContainerState.RUNNING
            ContainerState.fromString("Exited") shouldBe ContainerState.EXITED
        }

        test("returns null for a state it does not know") {
            ContainerState.fromString("teleporting") shouldBe null
            ContainerState.fromString("") shouldBe null
        }
    }
})
