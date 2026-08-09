package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.container.ContainerState
import es.jvbabi.docker.kt.support.*
import es.jvbabi.kfile.File
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val IMAGE = "alpine:latest"

/** High enough to stay clear of well-known services, below the ephemeral range the kernel hands out. */
private const val HOST_PORT = 58080
private const val CONTAINER_PORT = 80

/** Exposed without a host binding, to cover the exposed-only path next to the published one. */
private const val EXPOSED_ONLY_PORT = 9000

/**
 * Creates one container using every option `createContainer` accepts, checks that Docker reports all
 * of it back, and removes it again.
 *
 * Every resource is created for this run alone - container, named volume, network and a directory
 * below the system temp directory, each carrying the shared run id. [afterSpec] tears all four down
 * even when a test fails, so a run leaves the host as it found it.
 */
class ContainerLifecycleTest : FunSpec({

    tags(RequiresDocker)

    val containerName = testResourceName("lifecycle-container")
    val volumeName = testResourceName("lifecycle-volume")
    val networkName = testResourceName("lifecycle-network")
    val networkAlias = "docker-kt-lifecycle-alias"
    val hostDir = File.getTempDirectory().resolve(containerName)

    val labels = mapOf(
        "io.github.julius-babies.docker-kt.test" to "true",
        "io.github.julius-babies.docker-kt.resource" to containerName
    )

    val environment = mapOf(
        "DOCKER_KT_TEST" to "true",
        "DOCKER_KT_RESOURCE" to containerName
    )

    var networkId = ""

    beforeSpec {
        hostDir.mkdir(recursive = true)
        hostDir.resolve("marker.txt").writeText("docker.kt")

        withDocker { client ->
            client.ensureImage(IMAGE)
            client.networkBuilder(networkName) {
                labels { putAll(labels) }
            }.create()
            networkId = client.networks.getNetworks().first { it.name == networkName }.id
        }
    }

    afterSpec {
        withDocker { client ->
            client.removeContainerQuietly(containerName)
            client.removeNetworkQuietly(networkId)
            client.removeVolumeQuietly(volumeName)
        }
        if (hostDir.exists()) hostDir.delete(recursive = true)
    }

    test("creates a container with volumes, ports, labels, environment and a network") {
        withDocker { client ->
            val container = client.containerBuilder(IMAGE) {
                name = containerName
                healthCheck = Container.Healthcheck(test = listOf("CMD-SHELL", "true"))
                entrypoint = listOf("/bin/sh", "-c")
                cmd = listOf("sleep 3600")

                volumes {
                    bindHost(hostDir.absolutePath, "/mnt/host", readOnly = true)
                    bindVolume(volumeName, "/mnt/volume")
                }

                environment {
                    putAll(environment)
                }

                labels {
                    putAll(labels)
                }

                ports {
                    bind(CONTAINER_PORT, HOST_PORT, setOf(Container.PortBinding.Protocol.TCP))
                    expose(EXPOSED_ONLY_PORT)
                }

                networks {
                    connect(networkId, listOf(networkAlias))
                }
            }

            container.state shouldBeSameInstanceAs Container.State.NonExisting.Draft

            container.create()

            container.state shouldBeSameInstanceAs Container.State.Existing.Created
        }
    }

    test("inspect reports the labels, environment, command and ports it was created with") {
        withDocker { client ->
            val containerId = client.containerByName(containerName).shouldNotBeNull().id
            val inspect = client.containers.inspectContainer(containerId)

            inspect.config.labels shouldContainAll labels
            inspect.config.env shouldContainAll environment.map { (key, value) -> "$key=$value" }
            inspect.config.cmd shouldBe listOf("sleep 3600")

            // Both the published and the exposed-only port have to show up as exposed.
            inspect.config.exposedPorts shouldContainKey "$CONTAINER_PORT/tcp"
            inspect.config.exposedPorts shouldContainKey "$EXPOSED_ONLY_PORT/udp"

            // Only the published one gets a host binding.
            val binding = inspect.hostConfig.portBindings["$CONTAINER_PORT/tcp"].shouldNotBeNull()
            binding.single().hostPort shouldBe HOST_PORT.toString()
            inspect.hostConfig.portBindings shouldNotContainKey "$EXPOSED_ONLY_PORT/udp"

            // As long as the container has not run, Docker keys the endpoint by whatever the create
            // request used - the id - and leaves NetworkID empty. Starting it resolves both.
            val endpoint = inspect.networkSettings.networks[networkId].shouldNotBeNull()
            endpoint.aliases.shouldNotBeNull() shouldContain networkAlias
        }
    }

    test("the container list reports the host bind and the named volume") {
        withDocker { client ->
            val container = client.containerByName(containerName).shouldNotBeNull()

            val hostMount = container.mounts.single { it.destination == "/mnt/host" }
            hostMount.type shouldBe "bind"
            hostMount.source shouldBe hostDir.absolutePath
            hostMount.rw shouldBe false

            val volumeMount = container.mounts.single { it.destination == "/mnt/volume" }
            volumeMount.type shouldBe "volume"
            volumeMount.name shouldBe volumeName
            volumeMount.rw shouldBe true

            container.labels shouldContainAll labels
        }
    }

    test("starts the container and publishes the port on the host") {
        withDocker { client ->
            val containerId = client.containerByName(containerName).shouldNotBeNull().id
            client.containers.startContainer(containerId)

            val inspect = client.containers.inspectContainer(containerId)
            inspect.state.running shouldBe true
            inspect.state.status shouldBe "running"

            // Docker publishes one entry per host address family, so check that ours is among them.
            val published = inspect.networkSettings.ports["$CONTAINER_PORT/tcp"].shouldNotBeNull()
            published.map { it.hostPort } shouldContain HOST_PORT.toString()

            // The exposed-only port shows up without any binding behind it.
            inspect.networkSettings.ports.keys shouldContain "$EXPOSED_ONLY_PORT/udp"
            inspect.networkSettings.ports["$EXPOSED_ONLY_PORT/udp"] shouldBe null

            // Now that the container ran, the endpoint is keyed by name and carries the network id.
            val network = inspect.networkSettings.networks[networkName].shouldNotBeNull()
            network.networkId shouldBe networkId

            client.containerByName(containerName).shouldNotBeNull().state shouldBe ContainerState.RUNNING
        }
    }

    test("getById hands back the running container with its state read from the daemon") {
        withDocker { client ->
            val containerId = client.containerByName(containerName).shouldNotBeNull().id

            val fetched = client.containers.getById(containerId)

            fetched.shouldNotBeNull()
            fetched.id shouldBe containerId
            // Inspect reports the name with a leading slash, the object carries it without.
            fetched.name shouldBe containerName
            fetched.state shouldBeSameInstanceAs Container.State.Existing.Running

            // The configuration comes back from the daemon rather than from the builder.
            fetched.labels shouldContainAll labels
            fetched.environment shouldContainAll environment
            fetched.cmd shouldBe listOf("sleep 3600")
            fetched.entrypoint shouldBe listOf("/bin/sh", "-c")
            fetched.healthCheck.shouldNotBeNull().test shouldBe listOf("CMD-SHELL", "true")
            fetched.volumes.map { it.containerPath } shouldContainExactlyInAnyOrder
                listOf("/mnt/host", "/mnt/volume")
            fetched.ports.single().hostPort shouldBe HOST_PORT
        }
    }

    test("getById returns null for an id the daemon does not know") {
        withDocker { client ->
            // Well-formed but never handed out - Docker answers 404 rather than failing the request.
            client.containers.getById("0".repeat(64)) shouldBe null
        }
    }

    test("removes the container once it has been stopped") {
        withDocker { client ->
            val containerId = client.containerByName(containerName).shouldNotBeNull().id
            val container = client.containers.getById(containerId).shouldNotBeNull()

            client.containers.stopContainer(containerId)
            delay(2.seconds)
            client.containerByName(containerName).shouldNotBeNull().state shouldBe ContainerState.EXITED

            container.remove()

            container.state shouldBeSameInstanceAs Container.State.NonExisting.Deleted
            client.containerByName(containerName) shouldBe null
        }
    }

    test("a removed container can neither be removed nor created again") {
        withDocker { client ->
            val container = client.containerBuilder(IMAGE) { name = "$containerName-never-created" }

            // A draft was never sent anywhere, so there is nothing to remove.
            shouldThrow<IllegalStateException> { container.remove() }
            container.state shouldBeSameInstanceAs Container.State.NonExisting.Draft
        }
    }
})
