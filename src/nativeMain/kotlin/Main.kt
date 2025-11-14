import docker.client.DockerClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Docker.kt - Socket Client with Fallback Demo")
    println("=" .repeat(50))
    
    val client = DockerClient()
    
    try {
        println("Using socket: ${client.getSocketPath()}")
        println()
        
        // Test connectivity
        print("Testing Docker connection... ")
        val isConnected = client.ping()
        if (isConnected) {
            println("✓ Connected")
            
            // Get Docker version
            println("\nDocker Version:")
            println(client.version())
            
            // Get Docker info
            println("\nDocker Info:")
            println(client.info())
        } else {
            println("✗ Failed to connect")
            println("Please make sure Docker daemon is running.")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        println("\nSocket fallback attempted:")
        println("  1. Main socket: /var/run/docker.sock")
        println("  2. User socket: \$HOME/.docker/run/docker.sock")
    } finally {
        client.close()
    }
}
