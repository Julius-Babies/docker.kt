package es.jvbabi.docker.kt.image

import es.jvbabi.docker.kt.api.image.ImagePullStatus
import es.jvbabi.docker.kt.api.image.ImageRemoveStatus
import es.jvbabi.docker.kt.docker.DockerClient
import es.jvbabi.docker.kt.support.RequiresDocker
import es.jvbabi.docker.kt.support.withDocker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The smallest official image there is, so the pull stays quick, and a throwaway one that a
 * development host is unlikely to depend on.
 */
private const val IMAGE = "hello-world:latest"

/**
 * Pulls an image, checks that it arrives with its layer callbacks and shows up in the image list,
 * then removes it again and checks that Docker reports the untag and the delete.
 *
 * Unlike the other integration specs this one cannot work on a resource of its own - an image tag is
 * global. It therefore restores what it found: if the host already had [IMAGE], [afterSpec] pulls it
 * back, otherwise it makes sure the image is gone again.
 */
class ImageLifecycleTest : FunSpec({

    tags(RequiresDocker)

    val announcedLayers = mutableListOf<String>()
    val downloadStatuses = mutableListOf<Pair<String, ImagePullStatus>>()

    var wasPresentBefore = false

    suspend fun DockerClient.hasTestImage(): Boolean =
        images.getImages().any { IMAGE in it.repoTags }

    beforeSpec {
        withDocker { client ->
            wasPresentBefore = client.hasTestImage()
            // Start from a known state: pulling an image that is already there only produces
            // "already exists" messages, and the layer callbacks would never fire.
            if (wasPresentBefore) client.images.removeImage(IMAGE, force = true)
        }
    }

    afterSpec {
        withDocker { client ->
            val present = client.hasTestImage()
            if (wasPresentBefore && !present) {
                runCatching { client.images.pull(IMAGE, onDownload = { _, _ -> }) }
            } else if (!wasPresentBefore && present) {
                runCatching { client.images.removeImage(IMAGE, force = true) }
            }
        }
    }

    test("pull reports the layers it is about to fetch and their progress") {
        withDocker { client ->
            client.hasTestImage() shouldBe false

            client.images.pull(
                image = IMAGE,
                beforeDownload = { layers -> announcedLayers += layers },
                onDownload = { layer, status -> downloadStatuses += layer to status }
            )

            announcedLayers.shouldNotBeEmpty()
            downloadStatuses.shouldNotBeEmpty()

            // Every layer the progress callback talks about was announced up front.
            announcedLayers shouldContainAll downloadStatuses.map { (layer, _) -> layer }.distinct()

            // A finished pull has to report each announced layer as downloaded.
            val downloaded = downloadStatuses
                .filter { (_, status) -> status is ImagePullStatus.Downloaded }
                .map { (layer, _) -> layer }
            downloaded shouldContainAll announcedLayers
        }
    }

    test("the pulled image shows up in the image list") {
        withDocker { client ->
            val image = client.images.getImages().find { IMAGE in it.repoTags }

            image.shouldNotBeNull()
            image.id shouldStartWith "sha256:"
            image.size shouldBeGreaterThan 0L
            image.repoDigests.shouldNotBeEmpty()
        }
    }

    test("remove untags the image and deletes its layers") {
        withDocker { client ->
            val imageId = client.images.getImages().first { IMAGE in it.repoTags }.id

            val statuses = client.images.removeImage(IMAGE)

            statuses.shouldNotBeEmpty()
            statuses.map { it.type } shouldContain ImageRemoveStatus.Type.Untagged
            statuses
                .filter { it.type == ImageRemoveStatus.Type.Untagged }
                .map { it.id } shouldContain IMAGE
            statuses
                .filter { it.type == ImageRemoveStatus.Type.Deleted }
                .map { it.id } shouldContain imageId

            client.hasTestImage() shouldBe false
        }
    }
})
