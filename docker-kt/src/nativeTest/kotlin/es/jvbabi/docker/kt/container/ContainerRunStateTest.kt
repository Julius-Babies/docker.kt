package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.ensureImage
import es.jvbabi.docker.kt.support.removeContainerQuietly
import es.jvbabi.docker.kt.support.testResourceName
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration.Companion.seconds

private const val IMAGE = "alpine:latest"

/**
 * Walks one container through the run states: start, pause and resume, stop, kill - checking after
 * every step that the daemon and the container's own state agree, and that the guards refuse a
 * transition the container is in no shape for.
 */
class ContainerRunStateTest : FunSpec({

    tags(RequiresDocker)

    val containerName = testResourceName("run-state-container")

    // The object is carried from test to test, so one client has to outlive the whole spec.
    lateinit var client: DockerClient
    lateinit var container: Container

    /** What the daemon says, as opposed to what the object believes. */
    suspend fun reportedStatus(): String =
        client.containers.inspectContainer(container.id).state.status

    beforeSpec {
        client = DockerClient()
        client.ensureImage(IMAGE)

        container = client.containerBuilder(IMAGE) {
            name = containerName
            entrypoint = listOf("/bin/sh", "-c")
            cmd = listOf("sleep 3600")
        }.apply { create() }
    }

    afterSpec {
        client.removeContainerQuietly(containerName)
        client.close()
    }

    test("start runs the container") {
        container.state shouldBeSameInstanceAs Container.State.Existing.Created

        container.start()

        container.state shouldBeSameInstanceAs Container.State.Existing.Running
        eventually(5.seconds) { reportedStatus() shouldBe "running" }
    }

    test("pause freezes it and resume thaws it again") {
        container.pause()

        container.state shouldBeSameInstanceAs Container.State.Existing.Paused
        eventually(5.seconds) { reportedStatus() shouldBe "paused" }

        // A paused container cannot be paused again.
        shouldThrow<IllegalStateException> { container.pause() }

        container.resume()

        container.state shouldBeSameInstanceAs Container.State.Existing.Running
        eventually(5.seconds) { reportedStatus() shouldBe "running" }

        // And a running one has nothing to resume.
        shouldThrow<IllegalStateException> { container.resume() }
    }

    test("stop shuts it down") {
        container.stop()

        container.state shouldBeSameInstanceAs Container.State.Existing.Stopped
        eventually(5.seconds) { reportedStatus() shouldBe "exited" }

        // Stopping twice would only earn a "not modified" from the daemon.
        shouldThrow<IllegalStateException> { container.stop() }
    }

    test("kill takes it down without a shutdown") {
        // Nothing to kill while it is down.
        shouldThrow<IllegalStateException> { container.kill() }

        container.start()
        eventually(5.seconds) { reportedStatus() shouldBe "running" }

        container.kill()

        container.state shouldBeSameInstanceAs Container.State.Existing.Stopped
        eventually(5.seconds) {
            val inspect = client.containers.inspectContainer(container.id)
            inspect.state.status shouldBe "exited"
            // SIGKILL, so the exit code carries the signal rather than a clean 0.
            inspect.state.exitCode shouldBe 137
        }
    }

    test("a draft has no run state to change") {
        val draft = client.containerBuilder(IMAGE) { name = "$containerName-draft" }

        shouldThrow<IllegalStateException> { draft.start() }
        shouldThrow<IllegalStateException> { draft.stop() }
        shouldThrow<IllegalStateException> { draft.pause() }
        shouldThrow<IllegalStateException> { draft.resume() }
        shouldThrow<IllegalStateException> { draft.kill() }

        draft.state shouldBeSameInstanceAs Container.State.NonExisting.Draft
        client.containers.getContainers(all = true)
            .find { it.name == "$containerName-draft" }.shouldBe(null)
    }
})
