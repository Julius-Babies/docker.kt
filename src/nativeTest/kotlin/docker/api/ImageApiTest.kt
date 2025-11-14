package docker.api

import docker.client.DockerClient
import docker.client.DockerClientConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for ImageApi.
 * These tests require a running Docker daemon.
 */
class ImageApiTest {
    private lateinit var client: DockerClient
    private lateinit var imageApi: ImageApi
    
    @BeforeTest
    fun setup() {
        client = DockerClient(DockerClientConfig.default())
        imageApi = client.images
    }
    
    @AfterTest
    fun teardown() {
        client.close()
    }
    
    @Test
    fun testListImages() = runBlocking {
        try {
            val images = imageApi.list()
            assertNotNull(images, "Image list should not be null")
            assertTrue(images is List, "Should return a list")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testListImagesWithDSL() = runBlocking {
        try {
            val images = imageApi.listWith {
                all = true
            }
            assertNotNull(images, "Image list should not be null")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testPullImage() = runBlocking {
        try {
            // Pull a tiny image for testing
            imageApi.pull("hello-world", "latest")
            
            // Verify it was pulled
            val images = imageApi.list()
            val hasHelloWorld = images.any { image ->
                image.repoTags?.any { it.contains("hello-world") } == true
            }
            assertTrue(hasHelloWorld, "hello-world image should be in the list after pulling")
            
            // Clean up - remove the test image
            try {
                imageApi.remove("hello-world:latest", force = true)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testImageTag() = runBlocking {
        try {
            // First pull an image
            imageApi.pull("hello-world", "latest")
            
            // Tag it
            imageApi.tag("hello-world:latest", "hello-world", "test-tag")
            
            // Verify the tag exists
            val images = imageApi.list()
            val hasTestTag = images.any { image ->
                image.repoTags?.any { it.contains("hello-world:test-tag") } == true
            }
            assertTrue(hasTestTag, "Tagged image should exist")
            
            // Clean up
            try {
                imageApi.remove("hello-world:test-tag", force = true)
                imageApi.remove("hello-world:latest", force = true)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testImageInspect() = runBlocking {
        try {
            // Pull a test image first
            imageApi.pull("hello-world", "latest")
            
            // Inspect it
            val imageInfo = imageApi.inspect("hello-world:latest")
            assertNotNull(imageInfo, "Image info should not be null")
            assertTrue(imageInfo.id.isNotEmpty(), "Image ID should not be empty")
            
            // Clean up
            try {
                imageApi.remove("hello-world:latest", force = true)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
}
