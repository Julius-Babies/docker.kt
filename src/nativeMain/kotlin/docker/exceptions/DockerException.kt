package docker.exceptions

/**
 * Base exception for all Docker-related errors.
 */
open class DockerException(message: String, cause: Throwable? = null) : Exception(message, cause)
