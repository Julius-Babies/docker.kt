package es.jvbabi.docker.kt

import es.jvbabi.docker.kt.api.container.Container
import es.jvbabi.docker.kt.api.container.ContainerState
import es.jvbabi.docker.kt.api.container.api.DockerContainer
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.kfile.File
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.delete
import kotlinx.coroutines.delay
import kotlin.time.Clock
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
 * below the system temp directory, each carrying a per-run id so concurrent runs cannot collide.
 * [afterSpec] tears all four down even when a test fails, so a run leaves the host as it found it.
 */
class ContainerLifecycleTest : FunSpec({

    val runId = Clock.System.now().toEpochMilliseconds()
    val containerName = "docker-kt-test-$runId"
    val volumeName = "docker-kt-test-volume-$runId"
    val networkName = "docker-kt-test-network-$runId"
    val networkAlias = "docker-kt-test-alias"
    val hostDir = File.getTempDirectory().resolve(containerName)

    val labels = mapOf(
        "io.github.julius-babies.docker-kt.test" to "true",
        "io.github.julius-babies.docker-kt.run-id" to runId.toString()
    )

    val environment = mapOf(
        "DOCKER_KT_TEST" to "true",
        "DOCKER_KT_RUN_ID" to runId.toString()
    )

    var networkId = ""

    suspend fun DockerClient.findTestContainer(): DockerContainer? =
        containers.getContainers(all = true).find { container ->
            container.names.any { it.trimStart('/') == containerName }
        }

    beforeSpec {
        hostDir.mkdir(recursive = true)
        hostDir.resolve("marker.txt").writeText("docker.kt")

        DockerClient().use { client ->
            // Compare the tag exactly: a "contains" match is already satisfied by any other
            // alpine-based image on the host, which would skip the pull and fail the create below.
            if (client.images.getImages().none { IMAGE in it.repoTags }) {
                client.images.pull(IMAGE, onDownload = { _, _ -> })
            }

            client.networks.createNetwork(name = networkName, labels = labels)
            networkId = client.networks.getNetworks().first { it.name == networkName }.id
        }
    }

    afterSpec {
        DockerClient().use { client ->
            client.findTestContainer()?.let { container ->
                runCatching { client.containers.killContainer(container.id) }
                runCatching { client.containers.deleteContainer(container.id) }
            }
            if (networkId.isNotEmpty()) runCatching { client.networks.removeNetwork(networkId) }
            // The library has no volume API yet, so the named volume goes back over the raw socket.
            runCatching { client.socket.delete("/volumes/$volumeName") }
        }
        if (hostDir.exists()) hostDir.delete(recursive = true)
    }

    test("creates a container with volumes, ports, labels, environment and a network") {
        DockerClient().use { client ->
            client.containers.createContainer(
                image = IMAGE,
                name = containerName,
                healthCheck = Container.Healthcheck(test = listOf("CMD-SHELL", "true")),
                volumeBinds = mapOf(
                    Container.VolumeBind.Host(hostDir.absolutePath, readOnly = true) to "/mnt/host",
                    Container.VolumeBind.Volume(volumeName) to "/mnt/volume"
                ),
                environment = environment,
                labels = labels,
                ports = listOf(
                    Container.PortBinding(
                        hostPort = HOST_PORT,
                        containerPort = CONTAINER_PORT,
                        protocol = Container.PortBinding.Protocol.TCP
                    )
                ),
                exposedPorts = mapOf(EXPOSED_ONLY_PORT to Container.PortBinding.Protocol.UDP),
                networkConfigs = listOf(
                    Container.NetworkConfig(networkId = networkId, aliases = listOf(networkAlias))
                ),
                entrypoint = listOf("/bin/sh", "-c"),
                cmd = listOf("sleep 3600")
            )

            val container = client.findTestContainer()
            container.shouldNotBeNull()
            container.state shouldBe ContainerState.CREATED
        }
    }

    test("inspect reports the labels, environment, command and ports it was created with") {
        DockerClient().use { client ->
            val containerId = client.findTestContainer().shouldNotBeNull().id
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
        DockerClient().use { client ->
            val container = client.findTestContainer().shouldNotBeNull()

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
        DockerClient().use { client ->
            val containerId = client.findTestContainer().shouldNotBeNull().id
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

            client.findTestContainer().shouldNotBeNull().state shouldBe ContainerState.RUNNING
        }
    }

    test("removes the container once it has been stopped") {
        DockerClient().use { client ->
            val containerId = client.findTestContainer().shouldNotBeNull().id

            client.containers.stopContainer(containerId)
            delay(2.seconds)
            client.findTestContainer().shouldNotBeNull().state shouldBe ContainerState.EXITED

            client.containers.deleteContainer(containerId)
            client.findTestContainer() shouldBe null
        }
    }
})
