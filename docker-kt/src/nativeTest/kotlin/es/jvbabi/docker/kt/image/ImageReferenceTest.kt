package es.jvbabi.docker.kt.image

import es.jvbabi.docker.kt.api.image.ImageApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the helpers that split an image reference into the parts the registry API needs.
 * Nothing here talks to a daemon, so the spec carries no `RequiresDocker` tag and runs anywhere.
 */
class ImageReferenceTest : FunSpec({

    context("registryFromImage") {
        test("falls back to Docker Hub for a bare name") {
            ImageApi.registryFromImage("alpine") shouldBe "docker.io"
            ImageApi.registryFromImage("alpine:3.19") shouldBe "docker.io"
        }

        test("falls back to Docker Hub for a namespaced name") {
            // "library" is a namespace, not a host - there is no dot to give it away.
            ImageApi.registryFromImage("library/alpine") shouldBe "docker.io"
            ImageApi.registryFromImage("julius-babies/docker-kt:1.0") shouldBe "docker.io"
        }

        test("takes the first segment once it looks like a host") {
            ImageApi.registryFromImage("ghcr.io/julius-babies/docker-kt") shouldBe "ghcr.io"
            ImageApi.registryFromImage("registry.example.com/team/app:2.1") shouldBe "registry.example.com"
        }

        test("treats localhost as a registry even without a dot") {
            ImageApi.registryFromImage("localhost/app") shouldBe "localhost"
        }
    }

    context("repositoryFromImage") {
        test("prefixes an official Docker Hub image with library/") {
            ImageApi.repositoryFromImage("alpine") shouldBe "library/alpine"
            ImageApi.repositoryFromImage("alpine:3.19") shouldBe "library/alpine"
        }

        test("leaves a repository that already has a namespace alone") {
            ImageApi.repositoryFromImage("julius-babies/docker-kt") shouldBe "julius-babies/docker-kt"
        }

        test("drops the tag but keeps the registry") {
            ImageApi.repositoryFromImage("ghcr.io/julius-babies/docker-kt:1.0") shouldBe
                "ghcr.io/julius-babies/docker-kt"
        }
    }

    context("tagFromImage") {
        test("defaults to latest when no tag is given") {
            ImageApi.tagFromImage("alpine") shouldBe "latest"
            ImageApi.tagFromImage("julius-babies/docker-kt") shouldBe "latest"
        }

        test("reads an explicit tag") {
            ImageApi.tagFromImage("alpine:3.19") shouldBe "3.19"
            ImageApi.tagFromImage("ghcr.io/julius-babies/docker-kt:1.2.3") shouldBe "1.2.3"
        }
    }
})
