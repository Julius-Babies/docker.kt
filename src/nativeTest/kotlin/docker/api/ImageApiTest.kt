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
    private var dockerAvailable = false
    
    @BeforeTest
    fun setup() {
        client = DockerClient(DockerClientConfig.default())
        imageApi = client.images
        
        // Check if Docker is available
        dockerAvailable = runBlocking {
            try {
                client.ping()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    @AfterTest
    fun teardown() {
        client.close()
    }
    
    private fun requireDocker() {
        if (!dockerAvailable) {
            println("Skipping test - Docker daemon is not available")
            return
        }
    }
    
    @Test
    fun testListImages() = runBlocking {
        requireDocker()
        if (!dockerAvailable) return@runBlocking
        
        val images = imageApi.list()
        assertNotNull(images, "Image list should not be null")
        assertTrue(images is List, "Should return a list")
    }
    
    @Test
    fun testListImagesWithDSL() = runBlocking {
        requireDocker()
        if (!dockerAvailable) return@runBlocking
        
        val images = imageApi.listWith {
            all = true
        }
        assertNotNull(images, "Image list should not be null")
    }
    
    @Test
    fun testPullImage() = runBlocking {
        requireDocker()
        if (!dockerAvailable) return@runBlocking
        
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
    }
    
    @Test
    fun testImageTag() = runBlocking {
        requireDocker()
        if (!dockerAvailable) return@runBlocking
        
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
    }
    
    @Test
    fun testImageInspect() = runBlocking {
        requireDocker()
        if (!dockerAvailable) return@runBlocking
        
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
    }
}
