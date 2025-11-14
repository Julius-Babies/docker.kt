[![official JetBrains project](https://jb.gg/badges/official-plastic.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)

# Docker.kt - Kotlin Native Docker Client

A Kotlin/Native library for interacting with Docker through Unix domain sockets. Pure Kotlin Native implementation using C-interop, no JVM dependencies.

## Features

- ✅ Unix socket communication with Docker daemon
- ✅ Automatic socket fallback (main socket → user socket)
- ✅ Pure Kotlin Native with C-interop (no JVM APIs)
- ✅ HTTP/1.1 protocol implementation
- ✅ Complete Docker API support:
  - **Images**: list, pull, push, tag, remove, inspect, prune
  - **Containers**: create, start, stop, restart, pause, unpause, kill, remove, list, inspect, logs, prune
  - **Volumes**: create, list, remove, inspect, prune
  - **Networks**: create, list, remove, inspect, connect, disconnect, prune
- ✅ Kotlin-style DSL for easy configuration
- ✅ Comprehensive English documentation

## Socket Fallback Mechanism

The client automatically detects and uses the best available Docker socket:

1. **Main socket**: `/var/run/docker.sock` (system-wide Docker daemon)
2. **User socket**: `$HOME/.docker/run/docker.sock` (rootless Docker)

The client uses the POSIX `stat()` system call to verify that the socket exists and is a valid Unix domain socket before attempting to connect.

## Usage

### Basic Connection

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

### Image Operations

```kotlin
val client = DockerClient()

// List all images
val images = client.images.list()

// List images with DSL
val allImages = client.images.listWith {
    all = true
    filters = "dangling=true"
}

// Pull an image
client.images.pull("nginx", tag = "latest")

// Tag an image
client.images.tag("nginx:latest", "myrepo/nginx", "v1.0")

// Inspect an image
val imageInfo = client.images.inspect("nginx:latest")
println("Image size: ${imageInfo.size}")

// Remove an image
client.images.remove("nginx:latest", force = true)

// Prune unused images
client.images.prune()
```

### Container Operations

```kotlin
val client = DockerClient()

// Create a container with DSL
val container = client.containers.createWith("my-nginx") {
    image = "nginx:latest"
    
    // Set environment variables
    env("NGINX_HOST", "example.com")
    env("NGINX_PORT", "80")
    
    // Expose ports
    expose(80)
    
    // Add labels
    label("app", "web")
    label("environment", "production")
    
    // Configure host settings
    host {
        bindPort(containerPort = 80, hostPort = 8080)
        bind("/host/path", "/container/path", "rw")
        restartPolicy = "always"
        memory = 512 * 1024 * 1024  // 512 MB
    }
    
    // Configure networking
    network {
        connectTo("my-network", ipv4Address = "172.20.0.2")
    }
}

// Start the container
client.containers.start(container.id)

// Get container logs
val logs = client.containers.logs(container.id, tail = 100, timestamps = true)
println(logs)

// Inspect container
val info = client.containers.inspect(container.id)
println("Container state: ${info.state?.status}")

// Stop the container
client.containers.stop(container.id, timeout = 10)

// Restart container
client.containers.restart(container.id)

// Pause/unpause
client.containers.pause(container.id)
client.containers.unpause(container.id)

// Remove the container
client.containers.remove(container.id, force = true, volumes = true)

// List containers with DSL
val runningContainers = client.containers.listWith {
    all = false
    size = true
}

// Prune stopped containers
client.containers.prune()
```

### Volume Operations

```kotlin
val client = DockerClient()

// Create a volume with DSL
val volume = client.volumes.createWith {
    name = "my-data"
    driver = "local"
    driverOption("type", "nfs")
    driverOption("device", ":/path/to/dir")
    label("backup", "daily")
}

// List volumes
val volumes = client.volumes.list()

// List volumes with DSL
val filteredVolumes = client.volumes.listWith {
    filters = "dangling=true"
}

// Inspect a volume
val volumeInfo = client.volumes.inspect("my-data")
println("Mountpoint: ${volumeInfo.mountpoint}")

// Remove a volume
client.volumes.remove("my-data")

// Prune unused volumes
val pruneResult = client.volumes.prune()
println("Space reclaimed: ${pruneResult.spaceReclaimed} bytes")
```

### Network Operations

```kotlin
val client = DockerClient()

// Create a network with DSL
val network = client.networks.createWith {
    name = "my-network"
    driver = "bridge"
    internal = false
    attachable = true
    
    // Configure IPAM
    ipam {
        driver = "default"
        subnet("172.20.0.0/16", gateway = "172.20.0.1")
        subnet("172.21.0.0/16", gateway = "172.21.0.1")
    }
    
    // Add labels
    label("environment", "production")
}

// List networks
val networks = client.networks.list()

// Inspect a network
val networkInfo = client.networks.inspect("my-network")
println("Network driver: ${networkInfo.driver}")

// Connect a container to a network
client.networks.connectContainer("my-network", "my-container") {
    ipv4Address = "172.20.0.10"
    aliases = listOf("web", "api")
}

// Disconnect a container from a network
client.networks.disconnectContainer("my-network", "my-container", force = true)

// Remove a network
client.networks.remove("my-network")

// Prune unused networks
val pruneResult = client.networks.prune()
println("Networks deleted: ${pruneResult.networksDeleted?.size ?: 0}")
```

## Architecture

- **DockerClient**: High-level Docker API wrapper with sub-APIs
  - `images`: ImageApi - Image management operations
  - `containers`: ContainerApi - Container lifecycle operations
  - `volumes`: VolumeApi - Volume management
  - `networks`: NetworkApi - Network management
- **HttpDockerClient**: HTTP/1.1 over Unix sockets
- **UnixSocketClient**: Low-level socket operations (C-interop)
- **DockerClientConfig**: Configuration with socket detection

## API Structure

```
docker/
├── client/
│   ├── DockerClient.kt           # Main client with API access
│   ├── HttpDockerClient.kt       # HTTP communication layer
│   ├── UnixSocketClient.kt       # Unix socket layer
│   └── DockerClientConfig.kt     # Configuration
├── api/
│   ├── ImageApi.kt               # Image operations
│   ├── ContainerApi.kt           # Container operations
│   ├── VolumeApi.kt              # Volume operations
│   └── NetworkApi.kt             # Network operations
├── models/
│   ├── Image.kt                  # Image data models
│   ├── Container.kt              # Container data models
│   ├── Volume.kt                 # Volume data models
│   └── Network.kt                # Network data models
└── exceptions/
    └── DockerException.kt        # Error handling
```

## Testing

The library includes comprehensive tests for all API operations:

```bash
./gradlew test
```

Tests require a running Docker daemon and will automatically skip if Docker is unavailable.

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
