package es.jvbabi.docker.kt.dto

import es.jvbabi.docker.kt.docker.dockerJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Docker does not always leave a field out when it has nothing to report - depending on the
 * platform and the endpoint it sends an explicit null instead. A default on the property does not
 * cover that on its own, which is what `coerceInputValues` in [dockerJson] is for.
 *
 * These are the shapes that took the whole suite down on a Linux daemon while macOS was green, so
 * they are pinned here rather than left to an integration run on the right operating system.
 * Nothing talks to a daemon, so the spec carries no `RequiresDocker` tag.
 */
class NullCoercionTest : FunSpec({

    test("an inspect whose collections are null reads as one with empty collections") {
        val json = """
            {
              "Id": "abc123",
              "Name": "/example",
              "Image": "alpine:latest",
              "State": {
                "Status": "running", "Running": true, "Paused": false, "Restarting": false,
                "OOMKilled": false, "Dead": false, "ExitCode": 0,
                "StartedAt": "2026-08-10T06:00:00Z", "FinishedAt": "0001-01-01T00:00:00Z"
              },
              "Config": {
                "Hostname": "abc123", "Domainname": "", "User": "",
                "AttachStdin": false, "AttachStdout": true, "AttachStderr": true,
                "ExposedPorts": null, "Tty": false, "OpenStdin": false, "StdinOnce": false,
                "Env": null, "Cmd": null, "Image": "alpine:latest", "Labels": null,
                "WorkingDir": ""
              },
              "HostConfig": { "NetworkMode": "default", "PortBindings": null },
              "NetworkSettings": { "Networks": null, "Ports": null },
              "Mounts": null
            }
        """.trimIndent()

        val inspect = dockerJson.decodeFromString<Inspect>(json)

        inspect.id shouldBe "abc123"
        inspect.hostConfig.portBindings.shouldBeEmpty()
        inspect.config.exposedPorts.shouldBeEmpty()
        inspect.config.env.shouldBeEmpty()
        inspect.config.labels.shouldBeEmpty()
        inspect.networkSettings.networks.shouldBeEmpty()
        inspect.networkSettings.ports.shouldBeEmpty()
        inspect.mounts.shouldBeEmpty()
    }

    test("an endpoint that carries no addresses still reads") {
        val json = """
            {
              "NetworkID": "net123",
              "EndpointID": "ep123",
              "IPAMConfig": null,
              "Aliases": null,
              "IPAddress": "172.17.0.2"
            }
        """.trimIndent()

        val endpoint = dockerJson.decodeFromString<InspectNetwork>(json)

        endpoint.networkId shouldBe "net123"
        endpoint.ipAddress shouldBe "172.17.0.2"
        endpoint.ipamConfig shouldBe null
        endpoint.aliases shouldBe null
        // Left out entirely rather than nulled, the other half of the same problem.
        endpoint.gateway shouldBe ""
        endpoint.globalIPv6Address shouldBe ""
    }
})
