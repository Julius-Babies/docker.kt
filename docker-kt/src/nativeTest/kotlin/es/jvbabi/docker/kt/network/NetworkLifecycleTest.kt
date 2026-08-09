package es.jvbabi.docker.kt.network

import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.testResourceName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlin.time.Clock

/**
 * Walks a network through its whole life: configured through the builder, created on the daemon,
 * then removed again through [Network.remove] - checking after every step that the daemon and the
 * object's own [Network.state] agree.
 *
 * The network is named for this run, and [afterSpec] drops it even if a test failed halfway.
 */
class NetworkLifecycleTest : FunSpec({

    tags(RequiresDocker)

    val networkName = testResourceName("network-lifecycle")
    val labels = mapOf(
        "io.github.julius-babies.docker-kt.test" to "true",
        "io.github.julius-babies.docker-kt.resource" to networkName
    )

    // A Network keeps the client it was built from, so one client has to outlive the whole spec:
    // with a client per test the object would be talking through a closed socket by the second step.
    lateinit var client: DockerClient
    lateinit var network: Network

    beforeSpec { client = DockerClient() }

    afterSpec {
        client.networks.getNetworks()
            .filter { it.name == networkName }
            .forEach { runCatching { it.remove() } }
        client.close()
    }

    test("the builder returns a draft that the daemon knows nothing about yet") {
        network = client.networkBuilder(networkName) {
            driver = Network.Driver.Bridge
            attachable = true
            internal = true

            labels { putAll(labels) }
        }

        network.state shouldBeSameInstanceAs Network.State.Draft
        network.name shouldBe networkName
        // Nothing has been created yet, so there is no creation time to report.
        network.created shouldBe null

        client.networks.getNetworks().none { it.name == networkName } shouldBe true
    }

    test("create registers the network and hands back its id") {
        network.create()

        network.state shouldBeSameInstanceAs Network.State.Created
        network.id.shouldNotBeEmpty()

        val created = client.networks.getNetworks().find { it.name == networkName }
        created.shouldNotBeNull()
        // The id the create response reported has to be the one the daemon lists it under.
        created.id shouldBe network.id
        created.driver shouldBe Network.Driver.Bridge
        created.internal shouldBe true
        created.attachable shouldBe true
        created.labels shouldContainAll labels
    }

    test("getById hands back an existing network ready to work with") {
        val fetched = client.networks.getById(network.id)

        fetched.shouldNotBeNull()
        // Not the object we created it from, but describing the same network.
        fetched shouldNotBeSameInstanceAs network
        fetched.id shouldBe network.id
        fetched.name shouldBe networkName
        fetched.state shouldBeSameInstanceAs Network.State.Created
        fetched.driver shouldBe Network.Driver.Bridge
        fetched.internal shouldBe true
        fetched.labels shouldContainAll labels
        // Only a network that exists has one, and it cannot lie in the future.
        fetched.created.shouldNotBeNull() shouldBeLessThanOrEqualTo Clock.System.now()
    }

    test("getById returns null for an id the daemon does not know") {
        // Well-formed but never handed out - Docker answers 404 rather than failing the request.
        client.networks.getById("0".repeat(64)) shouldBe null
    }

    test("remove deletes the network and marks it as such") {
        network.remove()

        network.state shouldBeSameInstanceAs Network.State.Deleted

        client.networks.getNetworks().none { it.name == networkName } shouldBe true
    }

    test("refresh notices a network that was removed through another handle") {
        val other = client.networkBuilder(testResourceName("network-refresh")).apply { create() }
        val handle = client.networks.getById(other.id).shouldNotBeNull()

        handle.remove()

        // The first object still believes what it last saw.
        other.state shouldBeSameInstanceAs Network.State.Created

        other.refresh()
        other.state shouldBeSameInstanceAs Network.State.Deleted
    }

    test("a removed network can neither be removed nor created again") {
        // Both guards read the same state, so neither call reaches the daemon.
        shouldThrow<IllegalStateException> { network.remove() }
        shouldThrow<IllegalStateException> { network.create() }

        network.state shouldBeSameInstanceAs Network.State.Deleted
    }
})
