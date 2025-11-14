[![official JetBrains project](https://jb.gg/badges/official-plastic.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)

# Docker.kt - Kotlin Native Docker Client

A Kotlin/Native library for interacting with Docker through Unix domain sockets. Pure Kotlin Native implementation using C-interop, no JVM dependencies.

## Features

- ✅ Unix socket communication with Docker daemon
- ✅ Automatic socket fallback (main socket → user socket)
- ✅ Pure Kotlin Native with C-interop (no JVM APIs)
- ✅ HTTP/1.1 protocol implementation
- ✅ Docker API support (ping, version, info)

## Socket Fallback Mechanism

The client automatically detects and uses the best available Docker socket:

1. **Main socket**: `/var/run/docker.sock` (system-wide Docker daemon)
2. **User socket**: `$HOME/.docker/run/docker.sock` (rootless Docker)

The client uses the POSIX `stat()` system call to verify that the socket exists and is a valid Unix domain socket before attempting to connect.

## Usage

```kotlin
import docker.client.DockerClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = DockerClient()  // Automatic socket detection
    
    try {
        println("Using socket: ${client.getSocketPath()}")
        
        if (client.ping()) {
            println("✓ Connected to Docker!")
            println(client.version())
        }
    } finally {
        client.close()
    }
}
```

## Architecture

- **DockerClient**: High-level Docker API wrapper
- **HttpDockerClient**: HTTP/1.1 over Unix sockets
- **UnixSocketClient**: Low-level socket operations (C-interop)
- **DockerClientConfig**: Configuration with socket detection

## Implementation Details

See [SOCKET_CLIENT_IMPLEMENTATION.md](SOCKET_CLIENT_IMPLEMENTATION.md) for detailed technical documentation.

See [PROJEKTSTRUKTUR.md](PROJEKTSTRUKTUR.md) for the project structure and roadmap.

## Requirements

- Kotlin Native 2.2.20+
- Docker daemon running
- Access to Docker socket (user must be in `docker` group or using rootless Docker)

## Building

```bash
./gradlew build
```

## License

The template is licensed under [CC0](https://creativecommons.org/publicdomain/zero/1.0/deed.en).
