import docker.client.DockerClient
import docker.api.*
import docker.models.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Docker.kt - Comprehensive Docker API Demo")
    println("=" .repeat(70))
    
    val client = DockerClient()
    
    try {
        println("Using socket: ${client.getSocketPath()}")
        println()
        
        // Test connectivity
        print("Testing Docker connection... ")
        val isConnected = client.ping()
        if (!isConnected) {
            println("✗ Failed to connect")
            println("Please make sure Docker daemon is running.")
            return@runBlocking
        }
        println("✓ Connected")
        
        // Get Docker version
        println("\nDocker Version:")
        println(client.version())
        println()
        
        // Demo Image operations
        println("=" .repeat(70))
        println("IMAGE OPERATIONS")
        println("=" .repeat(70))
        
        println("\n1. Listing images:")
        val images = client.images.listWith {
            all = false
        }
        println("   Found ${images.size} image(s)")
        images.take(3).forEach { image ->
            val tags = image.repoTags?.joinToString(", ") ?: "none"
            println("   - $tags (${image.size / 1024 / 1024} MB)")
        }
        
        println("\n2. Pulling test image (hello-world)...")
        try {
            client.images.pull("hello-world", "latest")
            println("   ✓ Image pulled successfully")
        } catch (e: Exception) {
            println("   Note: ${e.message}")
        }
        
        // Demo Container operations
        println("\n" + "=" .repeat(70))
        println("CONTAINER OPERATIONS")
        println("=" .repeat(70))
        
        println("\n1. Creating container with DSL:")
        val container = client.containers.createWith("demo-container") {
            image = "hello-world:latest"
            label("demo", "true")
            label("purpose", "testing")
        }
        println("   ✓ Container created: ${container.id.take(12)}")
        
        println("\n2. Starting container:")
        client.containers.start(container.id)
        println("   ✓ Container started")
        
        // Wait a bit for it to finish
        kotlinx.coroutines.delay(1000)
        
        println("\n3. Getting container logs:")
        try {
            val logs = client.containers.logs(container.id, tail = 20)
            println("   Container output:")
            logs.split("\n").take(5).forEach { line ->
                if (line.isNotBlank()) println("   | $line")
            }
        } catch (e: Exception) {
            println("   Note: ${e.message}")
        }
        
        println("\n4. Listing all containers:")
        val containers = client.containers.listWith {
            all = true
        }
        println("   Found ${containers.size} container(s)")
        containers.filter { it.names.isNotEmpty() }.take(3).forEach { c ->
            val name = c.names.firstOrNull()?.removePrefix("/") ?: "unnamed"
            println("   - $name: ${c.state}")
        }
        
        println("\n5. Removing demo container:")
        client.containers.remove(container.id, force = true)
        println("   ✓ Container removed")
        
        // Demo Volume operations
        println("\n" + "=" .repeat(70))
        println("VOLUME OPERATIONS")
        println("=" .repeat(70))
        
        println("\n1. Creating volume with DSL:")
        val volume = client.volumes.createWith {
            name = "demo-volume"
            driver = "local"
            label("demo", "true")
            label("created-by", "docker.kt")
        }
        println("   ✓ Volume created: ${volume.name}")
        
        println("\n2. Inspecting volume:")
        val volumeInfo = client.volumes.inspect("demo-volume")
        println("   - Name: ${volumeInfo.name}")
        println("   - Driver: ${volumeInfo.driver}")
        println("   - Mountpoint: ${volumeInfo.mountpoint}")
        
        println("\n3. Listing volumes:")
        val volumes = client.volumes.list()
        println("   Found ${volumes.size} volume(s)")
        volumes.take(3).forEach { v ->
            println("   - ${v.name}: ${v.driver}")
        }
        
        println("\n4. Removing demo volume:")
        client.volumes.remove("demo-volume")
        println("   ✓ Volume removed")
        
        // Demo Network operations
        println("\n" + "=" .repeat(70))
        println("NETWORK OPERATIONS")
        println("=" .repeat(70))
        
        println("\n1. Listing networks:")
        val networks = client.networks.list()
        println("   Found ${networks.size} network(s)")
        networks.take(3).forEach { net ->
            println("   - ${net.name}: ${net.driver} (${net.scope})")
        }
        
        println("\n2. Creating network with DSL:")
        val network = client.networks.createWith {
            name = "demo-network"
            driver = "bridge"
            label("demo", "true")
            ipam {
                subnet("172.25.0.0/16", gateway = "172.25.0.1")
            }
        }
        println("   ✓ Network created: ${network.id.take(12)}")
        
        println("\n3. Inspecting network:")
        val networkInfo = client.networks.inspect(network.id)
        println("   - Name: ${networkInfo.name}")
        println("   - Driver: ${networkInfo.driver}")
        println("   - Scope: ${networkInfo.scope}")
        
        println("\n4. Removing demo network:")
        client.networks.remove(network.id)
        println("   ✓ Network removed")
        
        // Clean up test image
        println("\n" + "=" .repeat(70))
        println("CLEANUP")
        println("=" .repeat(70))
        println("\nRemoving test image (hello-world)...")
        try {
            client.images.remove("hello-world:latest", force = true)
            println("✓ Test image removed")
        } catch (e: Exception) {
            println("Note: ${e.message}")
        }
        
        println("\n" + "=" .repeat(70))
        println("Demo completed successfully!")
        println("=" .repeat(70))
        
    } catch (e: Exception) {
        println("\nError: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
