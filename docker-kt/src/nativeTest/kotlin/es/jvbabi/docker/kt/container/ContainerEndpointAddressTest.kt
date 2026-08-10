package es.jvbabi.docker.kt.container

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.api.Network
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.ensureImage
import es.jvbabi.docker.kt.support.removeContainerQuietly
import es.jvbabi.docker.kt.support.removeNetworkQuietly
import es.jvbabi.docker.kt.support.testResourceName
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.seconds

private const val IMAGE = "alpine:latest"

/**
 * The addresses an endpoint carries, which are the one part of a [Container.NetworkConfig] that is
 * not simply what the container was configured with: Docker hands them out when the container
 * starts and takes them back when it stops.
 */
class ContainerEndpointAddressTest : FunSpec({

    tags(RequiresDocker)

    val containerName = testResourceName("endpoint-address-container")
    val networkName = testResourceName("endpoint-address-network")

    // The objects are carried from test to test, so one client has to outlive the whole spec.
    lateinit var client: DockerClient
    lateinit var network: Network
    lateinit var container: Container

    /** This container's endpoint on the test network, as of the last read. */
    fun endpoint(): Container.NetworkConfig =
        container.networks.first { it.network.name == networkName }

    beforeSpec {
        client = DockerClient()
        client.ensureImage(IMAGE)

        network = client.networkBuilder(networkName).apply { create() }
        container = client.containerBuilder(IMAGE) {
            name = containerName
            entrypoint = listOf("/bin/sh", "-c")
            cmd = listOf("sleep 3600")
            networks { connect(network, listOf("by-alias")) }
        }.apply { create() }
    }

    afterSpec {
        client.removeContainerQuietly(containerName)
        client.removeNetworkQuietly(network.id)
        client.close()
    }

    test("a container that has never run is attached, but has no address yet") {
        container.refresh()

        endpoint().ipv4Address.shouldBeNull()
        endpoint().ipv6Address.shouldBeNull()
    }

    test("starting the container is what fills the addresses in") {
        container.start()

        eventually(10.seconds) {
            container.refresh()
            endpoint().ipv4Address.shouldNotBeNull() shouldContain "."
            endpoint().ipv6Address.shouldNotBeNull() shouldContain ":"
        }

        // The alias is only reported once there is an endpoint to report it on.
        endpoint().aliases shouldContain "by-alias"
    }

    test("stopping it releases them again") {
        container.stop()

        eventually(10.seconds) {
            container.refresh()
            endpoint().ipv4Address.shouldBeNull()
            endpoint().ipv6Address.shouldBeNull()
        }
    }
})
