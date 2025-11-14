package docker.client

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class DockerClientConfigTest {
    
    @Test
    fun testDefaultConfigHasSocketPath() {
        val config = DockerClientConfig.default()
        assertNotNull(config.socketPath, "Socket path should not be null")
        assertTrue(
            config.socketPath.isNotEmpty(),
            "Socket path should not be empty"
        )
    }
    
    @Test
    fun testDefaultConfigHasVersion() {
        val config = DockerClientConfig.default()
        assertNotNull(config.version, "Version should not be null")
        assertTrue(
            config.version.isNotEmpty(),
            "Version should not be empty"
        )
    }
    
    @Test
    fun testSocketPathFallback() {
        // Test that config returns one of the expected socket paths
        val config = DockerClientConfig.default()
        val validPaths = listOf(
            "/var/run/docker.sock",
            // User socket paths can vary
        )
        
        val isValidOrUserSocket = validPaths.contains(config.socketPath) || 
                                  config.socketPath.contains("/.docker/run/docker.sock")
        
        assertTrue(
            isValidOrUserSocket,
            "Socket path should be either main socket or user socket, got: ${config.socketPath}"
        )
    }
    
    @Test
    fun testCustomSocketPath() {
        val customPath = "/custom/path/docker.sock"
        val config = DockerClientConfig(socketPath = customPath)
        assertTrue(
            config.socketPath == customPath,
            "Custom socket path should be preserved"
        )
    }
}
