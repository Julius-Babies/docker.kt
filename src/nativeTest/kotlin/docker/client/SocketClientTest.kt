package docker.client

import kotlinx.coroutines.runBlocking

/**
 * Simple test program to verify socket client implementation
 */
fun main() = runBlocking {
    println("Testing Docker Socket Client")
    println("=" .repeat(50))
    
    // Test socket detection
    val config = DockerClientConfig.default()
    println("Detected socket path: ${config.socketPath}")
    println()
    
    // Test connection
    val client = DockerClient(config)
    
    try {
        print("Testing ping... ")
        val pingResult = client.ping()
        if (pingResult) {
            println("✓ Success")
            
            println("\nDocker Version:")
            val version = client.version()
            println(version)
            
            println("\nDocker Info:")
            val info = client.info()
            println(info.take(500)) // Print first 500 chars
            if (info.length > 500) {
                println("... (truncated)")
            }
        } else {
            println("✗ Failed")
        }
    } catch (e: Exception) {
        println("✗ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
