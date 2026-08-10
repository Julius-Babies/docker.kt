package es.jvbabi.docker.kt.support

import es.jvbabi.docker.kt.api.Container
import es.jvbabi.docker.kt.docker.DockerClient
import io.kotest.core.Tag
import io.ktor.client.request.delete
import kotlin.time.Clock

/**
 * Marks a spec that talks to a real Docker daemon instead of running in isolation.
 *
 * Skip the whole integration suite - on a machine without a daemon, or to get a quick run - with
 * `KOTEST_TAGS='!RequiresDocker' ./gradlew :docker-kt:macosArm64Test`.
 */
object RequiresDocker : Tag()

/**
 * Identifies a single test binary run. Every throwaway resource carries it, so leftovers from a
 * crashed run are easy to spot and two runs on the same host cannot collide.
 */
val testRunId: Long = Clock.System.now().toEpochMilliseconds()

/**
 * Names a resource that exists for this run only, e.g. `docker-kt-lifecycle-container-1786298…`.
 * Keep [role] unique per spec: specs in one run share the id.
 */
fun testResourceName(role: String): String = "docker-kt-$role-$testRunId"

/** Runs [block] against a fresh client and closes it afterwards. */
suspend fun <T> withDocker(block: suspend (DockerClient) -> T): T = DockerClient().use { block(it) }

/** The container with exactly this name, or null. */
suspend fun DockerClient.containerByName(name: String): Container? = containers.getByName(name)

/**
 * Pulls [image] unless the host already has that exact tag.
 *
 * The exact match matters: a `contains` check would already be satisfied by any other image built
 * on the same base, the pull would be skipped and every later step would fail on a missing image.
 */
suspend fun DockerClient.ensureImage(image: String) {
    if (images.getImages().none { image in it.repoTags }) {
        images.pull(image, onDownload = { _, _ -> })
    }
}

/*
 * Teardown helpers. They swallow their errors on purpose: cleanup runs after a spec has possibly
 * already failed, and a secondary failure there would hide the one that matters.
 */

suspend fun DockerClient.removeContainerQuietly(name: String) {
    val container = runCatching { containerByName(name) }.getOrNull() ?: return
    runCatching { container.kill() }
    runCatching { container.remove() }
}

suspend fun DockerClient.removeNetworkQuietly(id: String) {
    if (id.isEmpty()) return
    runCatching { networks.getById(id)?.remove() }
}

/** The library has no volume API yet, so this one goes over the raw socket. */
suspend fun DockerClient.removeVolumeQuietly(name: String) {
    runCatching { socket.delete("/volumes/$name") }
}
