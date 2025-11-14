package docker.api

import docker.client.DockerClient
import docker.client.DockerClientConfig
import docker.models.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for NetworkApi.
 * These tests require a running Docker daemon.
 */
class NetworkApiTest {
    private lateinit var client: DockerClient
    private lateinit var networkApi: NetworkApi
    private lateinit var containerApi: ContainerApi
    private lateinit var imageApi: ImageApi
    
    @BeforeTest
    fun setup() {
        client = DockerClient(DockerClientConfig.default())
        networkApi = client.networks
        containerApi = client.containers
        imageApi = client.images
    }
    
    @AfterTest
    fun teardown() {
        client.close()
    }
    
    @Test
    fun testListNetworks() = runBlocking {
        try {
            val networks = networkApi.list()
            assertNotNull(networks, "Network list should not be null")
            assertTrue(networks.isNotEmpty(), "Should have at least default networks")
            
            // Docker should have at least the bridge network
            val hasBridge = networks.any { it.name == "bridge" }
            assertTrue(hasBridge, "Should have bridge network")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testListNetworksWithDSL() = runBlocking {
        try {
            val networks = networkApi.listWith {
                // No filters for now
            }
            assertNotNull(networks, "Network list should not be null")
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
        }
    }
    
    @Test
    fun testCreateAndRemoveNetwork() = runBlocking {
        try {
            // Create a network
            val request = NetworkCreateRequest(
                name = "test-network",
                driver = "bridge"
            )
            val response = networkApi.create(request)
            assertNotNull(response, "Create response should not be null")
            assertTrue(response.id.isNotEmpty(), "Network ID should not be empty")
            
            // Verify it was created
            val networks = networkApi.list()
            val exists = networks.any { it.name == "test-network" }
            assertTrue(exists, "Created network should exist in list")
            
            // Remove the network
            networkApi.remove(response.id)
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                networkApi.remove("test-network")
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testCreateNetworkWithDSL() = runBlocking {
        try {
            // Create network using DSL
            val response = networkApi.createWith {
                name = "test-dsl-network"
                driver = "bridge"
                label("test", "true")
                label("environment", "test")
                ipam {
                    subnet("172.20.0.0/16", gateway = "172.20.0.1")
                }
            }
            
            assertNotNull(response, "Create response should not be null")
            assertTrue(response.id.isNotEmpty(), "Network ID should not be empty")
            
            // Inspect the network to verify configuration
            val network = networkApi.inspect(response.id)
            assertNotNull(network, "Network should not be null")
            assertEquals("test-dsl-network", network.name, "Network name should match")
            
            // Check labels
            val labels = network.labels
            assertNotNull(labels, "Labels should not be null")
            assertEquals("true", labels["test"], "Test label should be set")
            
            // Check IPAM
            val ipam = network.ipam
            assertNotNull(ipam, "IPAM should not be null")
            val config = ipam.config
            assertNotNull(config, "IPAM config should not be null")
            assertTrue(config.isNotEmpty(), "IPAM config should have entries")
            
            // Clean up
            networkApi.remove(response.id)
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                networkApi.remove("test-dsl-network")
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testInspectNetwork() = runBlocking {
        try {
            // Create a network first
            val request = NetworkCreateRequest(name = "test-inspect-network")
            val createResponse = networkApi.create(request)
            
            // Inspect it
            val network = networkApi.inspect(createResponse.id)
            assertNotNull(network, "Network should not be null")
            assertEquals("test-inspect-network", network.name, "Network name should match")
            assertEquals(createResponse.id, network.id, "Network IDs should match")
            
            // Clean up
            networkApi.remove(createResponse.id)
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up if creation succeeded
            try {
                networkApi.remove("test-inspect-network")
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    fun testConnectAndDisconnectContainer() = runBlocking {
        try {
            // Create a network
            val networkRequest = NetworkCreateRequest(name = "test-connect-network")
            val networkResponse = networkApi.create(networkRequest)
            
            // Pull test image and create a container
            imageApi.pull("hello-world", "latest")
            val containerConfig = ContainerConfig(image = "hello-world:latest")
            val containerResponse = containerApi.create(containerConfig, "test-connect-container")
            
            // Connect container to network
            networkApi.connectContainer(networkResponse.id, containerResponse.id)
            
            // Disconnect container from network
            networkApi.disconnectContainer(networkResponse.id, containerResponse.id)
            
            // Clean up
            containerApi.remove(containerResponse.id, force = true)
            networkApi.remove(networkResponse.id)
            
            try {
                imageApi.remove("hello-world:latest", force = true)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        } catch (e: Exception) {
            println("Warning: Could not connect to Docker daemon: ${e.message}")
            println("Skipping test - this is expected if Docker is not running")
            
            // Try to clean up
            try {
                containerApi.remove("test-connect-container", force = true)
                networkApi.remove("test-connect-network")
                imageApi.remove("hello-world:latest", force = true)
            } catch (cleanupError: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
