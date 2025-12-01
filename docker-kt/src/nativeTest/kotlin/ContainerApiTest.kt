import es.jvbabi.docker.kt.api.container.ContainerState
import es.jvbabi.docker.kt.api.container.VolumeBind
import es.jvbabi.docker.kt.api.image.ImageNotFoundException
import es.jvbabi.docker.kt.docker.DockerClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ContainerApiTest : FunSpec({
    val testImageName = "alpine:latest"
    val testContainerName = "test-container-${Clock.System.now().epochSeconds}"

    beforeSpec {
        // Ensure test image is available
        DockerClient().use { client ->
            val images = client.images.getImages()
            val hasAlpine = images.any { image ->
                image.repoTags.any { it.contains("alpine") }
            }

            if (!hasAlpine) {
                println("Pulling alpine image for tests...")
                client.images.pull(testImageName, onDownload = { _, _ -> })
            }
        }
    }

    test("Get containers - should list all containers") {
        DockerClient().use { client ->
            val containers = client.containers.getContainers(all = true)
            println("Found ${containers.size} containers")
            containers.forEach { container ->
                println("  - ${container.names.firstOrNull()} (${container.state})")
            }
        }
    }

    test("Create container - should create a new container") {
        DockerClient().use { client ->
            // Cleanup vorheriger Test-Container
            cleanupTestContainer(client, testContainerName)

            client.containers.createContainer(
                image = testImageName,
                name = testContainerName,
                environment = mapOf(
                    "TEST_VAR" to "test_value",
                    "ANOTHER_VAR" to "another_value"
                ),
                labels = mapOf(
                    "test" to "true",
                    "created-by" to "kotest"
                )
            )

            val containers = client.containers.getContainers(all = true)
            val createdContainer = containers.find {
                it.names.any { name -> name.contains(testContainerName) }
            }

            createdContainer shouldNotBe null
            createdContainer!!.image shouldContain "alpine"
            createdContainer.state shouldBe "created"
            createdContainer.labels["test"] shouldBe "true"
            createdContainer.labels["created-by"] shouldBe "kotest"

            // Cleanup
            cleanupTestContainer(client, testContainerName)
        }
    }

    test("Create container with non-existent image - should throw ImageNotFoundException") {
        DockerClient().use { client ->
            val exception = shouldThrow<ImageNotFoundException> {
                client.containers.createContainer(
                    image = "non-existent-image-12345:latest",
                    name = "should-not-exist"
                )
            }

            exception.message shouldContain "Image not found"
            exception.image shouldBe "non-existent-image-12345:latest"
        }
    }

    test("Start and stop container lifecycle") {
        DockerClient().use { client ->
            cleanupTestContainer(client, testContainerName)

            // Create container
            client.containers.createContainer(
                image = testImageName,
                name = testContainerName
            )

            var container = findContainer(client, testContainerName)
            container shouldNotBe null
            container!!.state shouldBe "created"

            // Start container
            client.containers.startContainer(container.id)
            container = findContainer(client, testContainerName)
            container!!.state shouldBe ContainerState.RUNNING

            // Stop container
            client.containers.stopContainer(container.id)
            delay(2.seconds)
            var stoppedContainer = findContainer(client, testContainerName)
            stoppedContainer!!.state shouldBe ContainerState.EXITED

            // Cleanup
            cleanupTestContainer(client, testContainerName)
        }
    }

    test("Restart container") {
        DockerClient().use { client ->
            cleanupTestContainer(client, testContainerName)

            // Create and start container
            client.containers.createContainer(
                image = testImageName,
                name = testContainerName
            )
            var container = findContainer(client, testContainerName)!!
            client.containers.startContainer(container.id)

            // Restart container
            client.containers.restartContainer(container.id)
            delay(2.seconds)

            val restartedContainer = findContainer(client, testContainerName)!!
            restartedContainer.state shouldBe ContainerState.RUNNING

            // Cleanup
            cleanupTestContainer(client, testContainerName)
        }
    }

    test("Pause and kill container") {
        DockerClient().use { client ->
            cleanupTestContainer(client, testContainerName)

            // Create and start container
            client.containers.createContainer(
                image = testImageName,
                name = testContainerName
            )
            var container = findContainer(client, testContainerName)!!
            client.containers.startContainer(container.id)

            // Pause container
            client.containers.pauseContainer(container.id)
            val pausedContainer = findContainer(client, testContainerName)!!
            pausedContainer.state shouldBe "paused"

            // Kill container (auch pausierte Container können gekillt werden)
            client.containers.killContainer(container.id)
            delay(500)

            val killedContainer = findContainer(client, testContainerName)!!
            killedContainer.state shouldBe "exited"

            // Cleanup
            cleanupTestContainer(client, testContainerName)
        }
    }

    test("Delete container") {
        DockerClient().use { client ->
            cleanupTestContainer(client, testContainerName)

            // Create container
            client.containers.createContainer(
                image = testImageName,
                name = testContainerName
            )

            var container = findContainer(client, testContainerName)
            container shouldNotBe null

            // Delete container
            client.containers.deleteContainer(container!!.id)

            // Verify deletion
            container = findContainer(client, testContainerName)
            container shouldBe null
        }
    }

    test("Create container with volume binds") {
        DockerClient().use { client ->
            val volumeTestName = "volume-test-${Clock.System.now().epochSeconds}"
            cleanupTestContainer(client, volumeTestName)

            // Create with volume bind
            client.containers.createContainer(
                image = testImageName,
                name = volumeTestName,
                volumeBinds = mapOf(
                    VolumeBind.Volume("my-test-volume") to "/data",
                    VolumeBind.Host("/tmp") to "/host-tmp"
                )
            )

            val container = findContainer(client, volumeTestName)
            container shouldNotBe null
            container!!.mounts.shouldNotBeEmpty()

            // Cleanup
            cleanupTestContainer(client, volumeTestName)
        }
    }

    test("Get containers - filter running only") {
        DockerClient().use { client ->
            val allContainers = client.containers.getContainers(all = true)
            val runningContainers = client.containers.getContainers(all = false)

            println("All containers: ${allContainers.size}")
            println("Running containers: ${runningContainers.size}")

            runningContainers.forEach { container ->
                container.state shouldBe "running"
            }
        }
    }
})

private suspend fun findContainer(client: DockerClient, name: String) =
    client.containers.getContainers(all = true).find {
        it.names.any { containerName -> containerName.contains(name) }
    }

private suspend fun cleanupTestContainer(client: DockerClient, name: String) {
    val container = findContainer(client, name)
    if (container != null) {
        try {
            // Try to stop if running
            if (container.state == ContainerState.RUNNING || container.state == ContainerState.PAUSED) {
                client.containers.killContainer(container.id)
                delay(2.seconds)
            }
            client.containers.deleteContainer(container.id)
        } catch (e: Exception) {
            println("Cleanup failed for $name: ${e.message}")
        }
    }
}

