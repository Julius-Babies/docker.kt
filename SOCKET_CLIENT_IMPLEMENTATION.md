# Docker Socket Client Implementation

This document describes the implementation of the Docker socket client with automatic fallback mechanism.

## Overview

The implementation provides a native Kotlin client for communicating with the Docker daemon through Unix domain sockets. It uses only Kotlin Native and C-interop, without any JVM dependencies.

## Architecture

### Components

1. **DockerClientConfig** - Configuration with automatic socket detection
2. **UnixSocketClient** - Low-level Unix socket operations via C-interop
3. **HttpDockerClient** - HTTP/1.1 protocol over Unix sockets
4. **DockerClient** - High-level Docker API wrapper

## Socket Fallback Mechanism

The socket fallback is implemented in `DockerClientConfig.default()`:

```kotlin
val config = DockerClientConfig.default()
// Automatically tries:
// 1. /var/run/docker.sock (main socket)
// 2. $HOME/.docker/run/docker.sock (user socket)
```

### Detection Logic

```kotlin
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
    
    // Default to main socket if neither exists
    return MAIN_SOCKET_PATH
}
```

### Socket Verification

The implementation verifies that a file is actually a socket using the `stat()` system call:

```kotlin
private fun socketExists(path: String): Boolean {
    memScoped {
        val statBuf = alloc<stat>()
        val result = stat(path, statBuf.ptr)
        
        if (result == 0) {
            // Check if it's a socket using bit masking
            val mode = statBuf.st_mode.toUInt()
            val fileType = mode and 0xF000u  // S_IFMT mask
            return fileType == 0xC000u       // S_IFSOCK value
        }
        return false
    }
}
```

## C-Interop Usage

The implementation uses Kotlin Native's C-interop to call POSIX functions directly:

### Socket Creation and Connection

```kotlin
// Create socket
socketFd = socket(AF_UNIX, SOCK_STREAM, 0)

// Setup address
val addr = alloc<sockaddr_un>()
addr.sun_family = AF_UNIX.convert()

// Copy path to sun_path
socketPath.encodeToByteArray().usePinned { pinned ->
    for (i in 0 until pathLen) {
        addr.sun_path[i] = pinned.get()[i].toByte()
    }
}

// Connect to socket
platform.posix.connect(socketFd, addr.ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
```

### Data Transfer

```kotlin
// Send data
data.usePinned { pinned ->
    send(socketFd, pinned.addressOf(0), data.size.convert(), 0)
}

// Receive data
val buffer = allocArray<ByteVar>(maxSize)
val received = recv(socketFd, buffer, maxSize.convert(), 0)
```

## HTTP Implementation

The `HttpDockerClient` implements HTTP/1.1 protocol manually:

### Request Building

```kotlin
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
```

### Response Parsing

```kotlin
private fun parseHttpResponse(data: ByteArray): HttpResponse {
    val response = data.decodeToString()
    val parts = response.split("\r\n\r\n", limit = 2)
    
    val headerSection = parts[0]
    val body = if (parts.size > 1) parts[1] else ""
    
    // Parse status line and headers...
    
    return HttpResponse(statusCode, headers, body)
}
```

## Usage Example

```kotlin
import docker.client.DockerClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = DockerClient()  // Uses automatic socket detection
    
    try {
        println("Using socket: ${client.getSocketPath()}")
        
        if (client.ping()) {
            println("Connected to Docker!")
            println(client.version())
        }
    } finally {
        client.close()
    }
}
```

## Technical Details

### Socket Type Detection

- Uses POSIX `stat()` system call
- Socket type is identified by file mode bits
- S_IFSOCK = 0xC000 (octal 0140000)
- File type mask S_IFMT = 0xF000
- Detection: `(st_mode & 0xF000) == 0xC000`

### Portability

The implementation is portable across POSIX systems:
- Uses standard POSIX socket APIs
- Manual bit-masking instead of macros for portability
- No JVM dependencies
- Works with Kotlin Native on Linux, macOS, etc.

### Error Handling

All socket operations include proper error handling:
- Checks return values from system calls
- Uses `strerror(errno)` for error messages
- Throws `DockerException` with descriptive messages
- Properly closes sockets on errors

## Testing

### Unit Tests

See `DockerClientConfigTest.kt` for configuration tests.

### Integration Test

The socket detection can be verified by running:

```bash
# Check socket availability
ls -la /var/run/docker.sock
ls -la $HOME/.docker/run/docker.sock

# Test connection manually
curl --unix-socket /var/run/docker.sock http://localhost/_ping
```

## Limitations

1. Currently implements basic GET, POST, DELETE operations
2. No streaming support for long-running operations yet
3. No connection pooling (creates new socket for each request)
4. Synchronous blocking operations (though wrapped in coroutines)

## Future Enhancements

1. Add support for more Docker API endpoints
2. Implement streaming for progress updates
3. Add connection pooling
4. Support for TCP sockets (not just Unix sockets)
5. TLS support for secure connections
