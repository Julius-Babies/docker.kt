@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.docker.kt.docker

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getenv

fun getSocketPath(): String {
    val systemPath = "/var/run/docker.sock"

    if (access(systemPath, F_OK) == 0) return systemPath

    val userPath = getenv("HOME")?.toKString()?.plus("/.docker/run/docker.sock") ?: throw IllegalStateException(
        "Could not find Docker socket. Tried $systemPath."
    )

    if (access(userPath, F_OK) == 0) return userPath

    throw IllegalStateException("Could not find Docker socket. Tried $systemPath and $userPath.")
}