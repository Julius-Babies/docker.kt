package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.ensureImage
import es.jvbabi.docker.kt.support.removeContainerQuietly
import es.jvbabi.docker.kt.support.testResourceName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private const val IMAGE = "alpine:latest"

/**
 * Attaching and detaching a container after it was created, from both ends: `container.connectTo`
 * and `network.connect` are the same operation read from either side, as are the two detach calls.
 *
 * Each step is checked against the daemon as well as against the container's own view, which the
 * calls keep in step.
 */
class ContainerNetworkAttachmentTest : FunSpec({

    tags(RequiresDocker)

    val containerName = testResourceName("attach-container")
    val fromContainerName = testResourceName("attach-from-container")
    val fromNetworkName = testResourceName("attach-from-network")

    // The objects are passed from test to test, so they all have to share one client: a Container or
    // Network keeps the client it came from, and a per-test client would be closed by the next step.
    lateinit var client: DockerClient
    lateinit var container: Container
    lateinit var viaContainer: Network
    lateinit var viaNetwork: Network

    /** What the daemon says the container is attached to, by network name. */
    suspend fun attachedNetworkNames(): Set<String> =
        client.containers.inspectContainer(container.id).networkSettings.networks.keys

    beforeSpec {
        client = DockerClient()
        client.ensureImage(IMAGE)

        viaContainer = client.networkBuilder(fromContainerName).apply { create() }
        viaNetwork = client.networkBuilder(fromNetworkName).apply { create() }

        // Created but never started: attaching does not need a running container.
        container = client.containerBuilder(IMAGE) { name = containerName }.apply { create() }
    }

    afterSpec {
        client.removeContainerQuietly(containerName)
        listOf(viaContainer, viaNetwork).forEach { network ->
            runCatching { client.networks.getById(network.id)?.remove() }
        }
        client.close()
    }

    test("a fresh container is on the default bridge only") {
        attachedNetworkNames() shouldBe setOf("bridge")

        // Built locally, so it only knows what it was configured with - not the bridge the daemon
        // attached on its own.
        container.networks.shouldBeEmpty()

        // Reading it back is what brings the daemon's view in.
        container.refresh()
        container.networks.map { it.network.name } shouldBe listOf("bridge")
    }

    test("connectTo attaches the container from the container's side") {
        container.connectTo(viaContainer, listOf("from-container"))

        attachedNetworkNames() shouldContain fromContainerName

        val endpoint = client.containers.inspectContainer(container.id)
            .networkSettings.networks[fromContainerName].shouldNotBeNull()
        endpoint.aliases.shouldNotBeNull() shouldContain "from-container"

        // The container's own view was updated along with the daemon's.
        container.networks.map { it.network.name } shouldContainExactlyInAnyOrder
            listOf("bridge", fromContainerName)
    }

    test("connect attaches the container from the network's side") {
        viaNetwork.connect(container, listOf("from-network"))

        attachedNetworkNames() shouldContain fromNetworkName

        val endpoint = client.containers.inspectContainer(container.id)
            .networkSettings.networks[fromNetworkName].shouldNotBeNull()
        endpoint.aliases.shouldNotBeNull() shouldContain "from-network"

        container.networks.map { it.network.name } shouldContainExactlyInAnyOrder
            listOf("bridge", fromContainerName, fromNetworkName)
    }

    test("disconnectFrom detaches the container from the container's side") {
        container.disconnectFrom(viaContainer)

        attachedNetworkNames() shouldNotContain fromContainerName
        container.networks.map { it.network.name } shouldContainExactlyInAnyOrder
            listOf("bridge", fromNetworkName)
    }

    test("disconnect detaches the container from the network's side") {
        viaNetwork.disconnect(container)

        attachedNetworkNames() shouldNotContain fromNetworkName
        container.networks.map { it.network.name } shouldBe listOf("bridge")
    }

    test("refresh picks up what another handle on the same container did") {
        val otherHandle = client.containers.getById(container.id).shouldNotBeNull()
        otherHandle.connectTo(viaContainer)

        // This handle knows nothing about it yet...
        container.networks.map { it.network.name } shouldBe listOf("bridge")

        container.refresh()
        container.networks.map { it.network.name } shouldContainExactlyInAnyOrder
            listOf("bridge", fromContainerName)

        container.disconnectFrom(viaContainer)
    }

    test("attaching to a network that does not exist yet is refused") {
        val draft = client.networkBuilder("$fromNetworkName-draft")

        shouldThrow<IllegalStateException> { container.connectTo(draft) }
        shouldThrow<IllegalStateException> { draft.connect(container) }

        // Nothing reached the daemon, so the container is still only on its bridge.
        container.networks.map { it.network.name } shouldBe listOf("bridge")
    }

    test("attaching a container that does not exist yet is refused") {
        val draftContainer = client.containerBuilder(IMAGE) { name = "$containerName-draft" }

        shouldThrow<IllegalStateException> { draftContainer.connectTo(viaNetwork) }
        shouldThrow<IllegalStateException> { viaNetwork.connect(draftContainer) }

        // Both detaches earlier left the container on the bridge Docker gave it, nothing else.
        client.containers.getById(container.id).shouldNotBeNull()
            .networks.map { it.network.name } shouldBe listOf("bridge")
    }
})
