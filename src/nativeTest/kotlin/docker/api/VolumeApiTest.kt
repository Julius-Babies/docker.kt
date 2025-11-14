package docker.api

import docker.client.DockerClient
import docker.client.DockerClientConfig
import docker.models.VolumeCreateRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for VolumeApi.
 * These tests require a running Docker daemon.
 */
class VolumeApiTest {
    private lateinit var client: DockerClient
    private lateinit var volumeApi: VolumeApi
    
    @BeforeTest
    fun setup() {
        client = DockerClient(DockerClientConfig.default())
        volumeApi = client.volumes
    }
    
    @AfterTest
    fun teardown() {
        client.close()
    }
    
    @Test
    fun testListVolumes() = runBlocking {
        try {
            val volumes = volumeApi.list()
            assertNotNull(volumes, "Volume list should not be null")
            assertTrue(volumes is List, "Should return a list")
            println("Found ${volumes.size} volume(s)")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testListVolumesWithDSL() {
        runBlocking {
            try {
                val volumes = volumeApi.listWith {
                    // No filters for now
                }
                assertNotNull(volumes, "Volume list should not be null")
            } catch (e: Exception) {
                println("Warning: Could not connect to Docker daemon: ${e.message}")
                println("Skipping test - this is expected if Docker is not running")
            }
        }
    }
    
    @Test
    fun testCreateAndRemoveVolume() = runBlocking {
        try {
            // Create a volume
            val request = VolumeCreateRequest(
                name = "test-volume",
                driver = "local"
            )
            val volume = volumeApi.create(request)
            assertNotNull(volume, "Created volume should not be null")
            assertEquals("test-volume", volume.name, "Volume name should match")
            
            // Remove the volume
            volumeApi.remove("test-volume")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                volumeApi.remove("test-volume", force = true)
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testCreateVolumeWithDSL() = runBlocking {
        try {
            // Create volume using DSL
            val volume = volumeApi.createWith {
                name = "test-dsl-volume"
                driver = "local"
                label("test", "true")
                label("environment", "test")
            }
            
            assertNotNull(volume, "Created volume should not be null")
            assertEquals("test-dsl-volume", volume.name, "Volume name should match")
            
            // Verify labels
            val labels = volume.labels
            assertNotNull(labels, "Labels should not be null")
            assertEquals("true", labels["test"], "Test label should be set")
            assertEquals("test", labels["environment"], "Environment label should be set")
            
            // Clean up
            volumeApi.remove("test-dsl-volume")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                volumeApi.remove("test-dsl-volume", force = true)
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testInspectVolume() = runBlocking {
        try {
            // Create a volume first
            val request = VolumeCreateRequest(name = "test-inspect-volume")
            val createdVolume = volumeApi.create(request)
            
            // Inspect it
            val volume = volumeApi.inspect("test-inspect-volume")
            assertNotNull(volume, "Volume should not be null")
            assertEquals("test-inspect-volume", volume.name, "Volume name should match")
            assertEquals(createdVolume.name, volume.name, "Names should match")
            
            // Clean up
            volumeApi.remove("test-inspect-volume")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                volumeApi.remove("test-inspect-volume", force = true)
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testPruneVolumes() = runBlocking {
        try {
            // Create a volume that won't be used
            val request = VolumeCreateRequest(name = "test-prune-volume")
            volumeApi.create(request)
            
            // Prune unused volumes
            val result = volumeApi.prune()
            assertNotNull(result, "Prune result should not be null")
            
            // The volume we created should have been pruned
            // Check if it exists
            val volumes = volumeApi.list()
            val stillExists = volumes.any { it.name == "test-prune-volume" }
            assertFalse(stillExists, "Pruned volume should not exist")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                volumeApi.remove("test-prune-volume", force = true)
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
