@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.docker.kt.docker

import es.jvbabi.kfile.File
import kotlinx.cinterop.ExperimentalForeignApi

fun getSocketPath(): String {

    val socketOptions = listOf(
        File("/var/run/docker.sock"),
        File.getUserHomeDirectory().resolve(".docker").resolve("run").resolve("docker.sock"),
        File.getUserHomeDirectory().resolve(".colima").resolve("default").resolve("docker.sock"),
    )
    val path = socketOptions.firstOrNull { it.exists() }
    if (path != null) return path.absolutePath

    throw IllegalStateException("Could not find Docker socket. Tried ${socketOptions.joinToString { it.absolutePath }}")
}