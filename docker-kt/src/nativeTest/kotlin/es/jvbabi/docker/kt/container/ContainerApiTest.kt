package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.image.ImageNotFoundException
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.ensureImage
import es.jvbabi.docker.kt.support.removeContainerQuietly
import es.jvbabi.docker.kt.support.testResourceName
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.seconds

private const val IMAGE = "alpine:latest"

/**
 * What is left of ContainerApi once containers do their own work: listing with and without the
 * stopped ones, restarting, and the error a missing image produces.
 *
 * Creating, starting, stopping, pausing, attaching to networks and removing are covered by
 * [ContainerLifecycleTest], [ContainerRunStateTest] and [ContainerNetworkAttachmentTest].
 */
class ContainerApiTest : FunSpec({

    tags(RequiresDocker)

    val containerName = testResourceName("api-container")

    // The container is carried from test to test, so one client has to outlive the whole spec.
    lateinit var client: DockerClient
    lateinit var container: Container

    beforeSpec {
        client = DockerClient()
        client.ensureImage(IMAGE)
    }

    afterSpec {
        client.removeContainerQuietly(containerName)
        client.close()
    }

    test("creating from an image the host does not have reports the image, not a transport error") {
        val missing = "docker-kt-no-such-image:latest"

        val exception = shouldThrow<ImageNotFoundException> {
            client.containerBuilder(missing) { name = "$containerName-never-created" }.create()
        }

        exception.image shouldBe missing
        exception.message shouldContain "Image not found"
    }

    test("restart takes a running container down and brings it back up") {
        container = client.containerBuilder(IMAGE) {
            name = containerName
            entrypoint = listOf("/bin/sh", "-c")
            cmd = listOf("sleep 3600")
        }.apply { create() }

        container.start()
        val firstStart = eventually(5.seconds) {
            client.containers.inspectContainer(container.id).state
                .also { it.running shouldBe true }
                .startedAt
        }

        client.containers.restartContainer(container.id)

        // Up again, but not the same run: the daemon reports a later start.
        eventually(10.seconds) {
            val state = client.containers.inspectContainer(container.id).state
            state.running shouldBe true
            state.startedAt shouldNotBe firstStart
        }
    }

    test("the listing leaves stopped containers out unless asked for all of them") {
        client.containers.getContainers(all = false).map { it.name } shouldContain containerName

        container.stop()

        client.containers.getContainers(all = false).map { it.name } shouldNotContain containerName
        client.containers.getContainers(all = true).map { it.name } shouldContain containerName
    }

    test("everything the listing hands out is a container that exists") {
        client.containers.getContainers(all = true).forEach { listed ->
            listed.id shouldNotBe ""
            // Whatever status the daemon reported, it mapped onto a state that means "it is there".
            listed.state.shouldBeInstanceOf<Container.State.Existing>()
        }
    }
})
