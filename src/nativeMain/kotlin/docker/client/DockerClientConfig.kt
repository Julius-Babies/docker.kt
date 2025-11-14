package docker.client

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Configuration for Docker client including socket paths and fallback behavior.
 */
data class DockerClientConfig(
    val socketPath: String,
    val version: String = "v1.41"
) {
    companion object {
        private const val MAIN_SOCKET_PATH = "/var/run/docker.sock"
        private const val USER_SOCKET_TEMPLATE = "/.docker/run/docker.sock"
        
        /**
         * Creates a default configuration with automatic socket detection and fallback.
         * First tries the main Docker socket at /var/run/docker.sock
         * If not available, falls back to the user socket at $HOME/.docker/run/docker.sock
         */
        fun default(): DockerClientConfig {
            val socketPath = detectSocketPath()
            return DockerClientConfig(socketPath = socketPath)
        }
        
        /**
         * Detects available Docker socket with fallback mechanism.
         * Returns the first available socket path.
         */
        private fun detectSocketPath(): String {
            // Try main socket first
            if (socketExists(MAIN_SOCKET_PATH)) {
                return MAIN_SOCKET_PATH
            }
            
            // Fallback to user socket
            val homeDir = getenv("HOME")?.toKString()
            if (homeDir != null) {
                val userSocketPath = "$homeDir$USER_SOCKET_TEMPLATE"
                if (socketExists(userSocketPath)) {
                    return userSocketPath
                }
            }
            
            // If neither exists, return main socket path (will fail on connection)
            return MAIN_SOCKET_PATH
        }
        
        /**
         * Checks if a socket file exists and is a socket.
         */
        private fun socketExists(path: String): Boolean {
            memScoped {
                val statBuf = alloc<stat>()
                val result = stat(path, statBuf.ptr)
                
                if (result == 0) {
                    // Check if it's a socket (S_IFSOCK)
                    val mode = statBuf.st_mode.toInt()
                    return (mode and S_IFSOCK.toInt()) != 0
                }
                return false
            }
        }
    }
}
