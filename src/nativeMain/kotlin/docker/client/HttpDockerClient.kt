package docker.client

import docker.exceptions.DockerException

/**
 * HTTP client for Docker API communication over Unix domain sockets.
 * Implements HTTP/1.1 protocol for REST API requests.
 */
class HttpDockerClient(private val config: DockerClientConfig) {
    private var socketClient: UnixSocketClient? = null
    
    /**
     * Performs a GET request to the Docker API.
     */
    suspend fun get(path: String): HttpResponse {
        return request("GET", path)
    }
    
    /**
     * Performs a POST request to the Docker API.
     */
    suspend fun post(path: String, body: String? = null): HttpResponse {
        return request("POST", path, body)
    }
    
    /**
     * Performs a DELETE request to the Docker API.
     */
    suspend fun delete(path: String): HttpResponse {
        return request("DELETE", path)
    }
    
    /**
     * Performs an HTTP request over Unix socket.
     */
    private fun request(method: String, path: String, body: String? = null): HttpResponse {
        // Create new socket for each request
        val client = UnixSocketClient(config.socketPath)
        
        try {
            client.connect()
            
            // Build HTTP request
            val request = buildHttpRequest(method, path, body)
            
            // Send request
            client.send(request)
            
            // Receive response
            val responseData = client.receiveAll()
            
            return parseHttpResponse(responseData)
        } finally {
            client.close()
        }
    }
    
    /**
     * Builds an HTTP/1.1 request string.
     */
    private fun buildHttpRequest(method: String, path: String, body: String? = null): String {
        val fullPath = "/${config.version}$path"
        
        val builder = StringBuilder()
        builder.append("$method $fullPath HTTP/1.1\r\n")
        builder.append("Host: localhost\r\n")
        builder.append("User-Agent: docker.kt/1.0\r\n")
        
        if (body != null) {
            builder.append("Content-Type: application/json\r\n")
            builder.append("Content-Length: ${body.length}\r\n")
        }
        
        builder.append("Connection: close\r\n")
        builder.append("\r\n")
        
        if (body != null) {
            builder.append(body)
        }
        
        return builder.toString()
    }
    
    /**
     * Parses HTTP response from raw bytes.
     */
    private fun parseHttpResponse(data: ByteArray): HttpResponse {
        if (data.isEmpty()) {
            throw DockerException("Empty response from Docker daemon")
        }
        
        val response = data.decodeToString()
        
        // Split headers and body
        val parts = response.split("\r\n\r\n", limit = 2)
        if (parts.isEmpty()) {
            throw DockerException("Invalid HTTP response format")
        }
        
        val headerSection = parts[0]
        val body = if (parts.size > 1) parts[1] else ""
        
        // Parse status line
        val lines = headerSection.split("\r\n")
        if (lines.isEmpty()) {
            throw DockerException("Invalid HTTP response: no status line")
        }
        
        val statusLine = lines[0]
        val statusParts = statusLine.split(" ", limit = 3)
        if (statusParts.size < 2) {
            throw DockerException("Invalid HTTP status line: $statusLine")
        }
        
        val statusCode = statusParts[1].toIntOrNull() 
            ?: throw DockerException("Invalid status code: ${statusParts[1]}")
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key.lowercase()] = value
            }
        }
        
        return HttpResponse(statusCode, headers, body)
    }
    
    /**
     * Closes the client and releases resources.
     */
    fun close() {
        socketClient?.close()
        socketClient = null
    }
}

/**
 * Represents an HTTP response from Docker API.
 */
data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String
) {
    fun isSuccessful(): Boolean = statusCode in 200..299
    
    fun isError(): Boolean = statusCode >= 400
}
