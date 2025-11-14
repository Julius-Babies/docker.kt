# Docker.kt - Projektstruktur und Planung

## Überblick

Dieses Dokument beschreibt die geplante Struktur für eine Kotlin/Native-Bibliothek zur Interaktion mit Docker über die Docker REST API.

## Projektziele

Die Bibliothek soll folgende Kernfunktionen bereitstellen:
- **Images pullen/updaten** mit Progress-Anzeige
- **Container erstellen** aus Images
- **Container löschen**
- **Container starten**
- **Container stoppen**

## Architektur-Übersicht

### Design-Prinzipien

1. **Objektorientierter Ansatz**: Klare Trennung von Verantwortlichkeiten
2. **API-basiert**: Nutzung der Docker Engine REST API
3. **Asynchron**: Nutzung von Kotlin Coroutines für nicht-blockierende Operationen
4. **Typsicherheit**: Stark typisierte Kotlin-Klassen für Docker-Objekte
5. **Progress-Tracking**: Event-basiertes System für Fortschrittsanzeige

## Modul-Struktur

```
docker.kt/
├── src/
│   ├── nativeMain/
│   │   └── kotlin/
│   │       ├── docker/
│   │       │   ├── client/
│   │       │   │   ├── DockerClient.kt          # Hauptklasse für Docker-Interaktion
│   │       │   │   ├── DockerClientConfig.kt    # Konfiguration (Socket-Pfad, etc.)
│   │       │   │   └── HttpDockerClient.kt      # HTTP/Unix-Socket Implementation
│   │       │   │
│   │       │   ├── api/
│   │       │   │   ├── ImageApi.kt              # Image-Operationen
│   │       │   │   ├── ContainerApi.kt          # Container-Operationen
│   │       │   │   └── SystemApi.kt             # System-Info (Version, Ping)
│   │       │   │
│   │       │   ├── models/
│   │       │   │   ├── Image.kt                 # Image-Datenmodell
│   │       │   │   ├── Container.kt             # Container-Datenmodell
│   │       │   │   ├── ContainerConfig.kt       # Container-Konfiguration
│   │       │   │   ├── HostConfig.kt            # Host-Konfiguration (Ports, Volumes)
│   │       │   │   └── ProgressDetail.kt        # Progress-Information beim Pull
│   │       │   │
│   │       │   ├── progress/
│   │       │   │   ├── ProgressListener.kt      # Interface für Progress-Updates
│   │       │   │   ├── PullProgress.kt          # Pull-Progress-Tracking
│   │       │   │   └── ProgressParser.kt        # JSON-Stream-Parser
│   │       │   │
│   │       │   └── exceptions/
│   │       │       ├── DockerException.kt       # Base-Exception
│   │       │       ├── ImageNotFoundException.kt
│   │       │       └── ContainerException.kt
│   │       │
│   │       └── Main.kt                          # Beispiel-Anwendung
│   │
│   └── nativeTest/
│       └── kotlin/
│           └── docker/
│               ├── client/
│               │   └── DockerClientTest.kt
│               ├── api/
│               │   ├── ImageApiTest.kt
│               │   └── ContainerApiTest.kt
│               └── models/
│                   └── ModelSerializationTest.kt
│
├── build.gradle.kts
└── README.md
```

## Klassendesign

### 1. DockerClient (Hauptklasse)

```kotlin
class DockerClient(
    private val config: DockerClientConfig = DockerClientConfig.default()
) {
    val images: ImageApi
    val containers: ContainerApi
    val system: SystemApi
    
    suspend fun ping(): Boolean
    suspend fun version(): VersionInfo
    fun close()
}
```

**Verantwortlichkeiten:**
- Zentrale Schnittstelle zur Docker-API
- Verwaltung der HTTP-Client-Verbindung
- Bereitstellung von Sub-APIs (Images, Container, System)

### 2. ImageApi

```kotlin
class ImageApi(private val client: HttpDockerClient) {
    suspend fun pull(
        name: String,
        tag: String = "latest",
        progressListener: ProgressListener? = null
    ): Image
    
    suspend fun list(all: Boolean = false): List<Image>
    
    suspend fun inspect(id: String): Image
    
    suspend fun remove(id: String, force: Boolean = false): Boolean
}
```

**Verantwortlichkeiten:**
- Image-Pull-Operationen mit Progress-Tracking
- Image-Listing und -Inspektion
- Image-Löschung

### 3. ContainerApi

```kotlin
class ContainerApi(private val client: HttpDockerClient) {
    suspend fun create(
        config: ContainerConfig
    ): Container
    
    suspend fun start(id: String): Boolean
    
    suspend fun stop(id: String, timeout: Int = 10): Boolean
    
    suspend fun remove(id: String, force: Boolean = false): Boolean
    
    suspend fun list(all: Boolean = false): List<Container>
    
    suspend fun inspect(id: String): Container
}
```

**Verantwortlichkeiten:**
- Container-Lifecycle-Management
- Container-Status-Abfragen

### 4. Datenmodelle

#### Image
```kotlin
@Serializable
data class Image(
    val id: String,
    val repoTags: List<String>,
    val created: Long,
    val size: Long,
    val virtualSize: Long
)
```

#### Container
```kotlin
@Serializable
data class Container(
    val id: String,
    val names: List<String>,
    val image: String,
    val imageId: String,
    val state: String,
    val status: String,
    val created: Long
)
```

#### ContainerConfig
```kotlin
@Serializable
data class ContainerConfig(
    val image: String,
    val cmd: List<String>? = null,
    val env: List<String>? = null,
    val exposedPorts: Map<String, Any>? = null,
    val hostConfig: HostConfig? = null,
    val name: String? = null
)
```

### 5. Progress-Tracking

```kotlin
interface ProgressListener {
    fun onProgress(status: String, current: Long, total: Long)
    fun onComplete()
    fun onError(error: String)
}

@Serializable
data class ProgressDetail(
    val status: String,
    val id: String? = null,
    val progress: String? = null,
    val progressDetail: ProgressDetailInfo? = null
)

@Serializable
data class ProgressDetailInfo(
    val current: Long = 0,
    val total: Long = 0
)
```

## Technische Implementierung

### Docker REST API Kommunikation

1. **Unix Socket Support**: Kommunikation über `/var/run/docker.sock`
2. **HTTP Client**: Ktor HTTP Client für API-Requests
3. **JSON Serialization**: kotlinx.serialization für Datenmodelle
4. **Streaming**: Unterstützung für JSON-Streaming bei Progress-Updates

### API-Endpunkte

| Operation | Methode | Endpunkt |
|-----------|---------|----------|
| Pull Image | POST | `/images/create?fromImage={name}&tag={tag}` |
| List Images | GET | `/images/json` |
| Create Container | POST | `/containers/create` |
| Start Container | POST | `/containers/{id}/start` |
| Stop Container | POST | `/containers/{id}/stop` |
| Remove Container | DELETE | `/containers/{id}` |
| List Containers | GET | `/containers/json` |

### Konfiguration

```kotlin
data class DockerClientConfig(
    val host: String = "unix:///var/run/docker.sock",
    val version: String = "v1.41",
    val timeout: Duration = 30.seconds
) {
    companion object {
        fun default() = DockerClientConfig()
    }
}
```

## Beispiel-Verwendung

```kotlin
fun main() = runBlocking {
    val docker = DockerClient()
    
    try {
        // System-Info prüfen
        println("Docker Version: ${docker.version()}")
        
        // Image pullen mit Progress
        println("\nPulling nginx:latest...")
        docker.images.pull(
            name = "nginx",
            tag = "latest",
            progressListener = object : ProgressListener {
                override fun onProgress(status: String, current: Long, total: Long) {
                    val percent = if (total > 0) (current * 100 / total) else 0
                    print("\r$status: $percent%")
                }
                
                override fun onComplete() {
                    println("\nPull complete!")
                }
                
                override fun onError(error: String) {
                    println("\nError: $error")
                }
            }
        )
        
        // Container erstellen
        val container = docker.containers.create(
            ContainerConfig(
                image = "nginx:latest",
                name = "my-nginx",
                hostConfig = HostConfig(
                    portBindings = mapOf("80/tcp" to listOf(PortBinding("0.0.0.0", "8080")))
                )
            )
        )
        println("\nContainer created: ${container.id}")
        
        // Container starten
        docker.containers.start(container.id)
        println("Container started")
        
        // Container-Liste anzeigen
        val containers = docker.containers.list(all = true)
        println("\nRunning containers:")
        containers.forEach { println("  - ${it.names.firstOrNull()} (${it.state})") }
        
        // Container stoppen
        docker.containers.stop(container.id)
        println("\nContainer stopped")
        
        // Container löschen
        docker.containers.remove(container.id)
        println("Container removed")
        
    } finally {
        docker.close()
    }
}
```

## Nächste Schritte

### Phase 1: Grundgerüst (Minimal Viable Product)
1. ✅ Projekt-Struktur definieren
2. ⬜ DockerClient und HttpDockerClient implementieren
3. ⬜ Basis-Datenmodelle erstellen (Image, Container)
4. ⬜ SystemApi für Ping/Version implementieren

### Phase 2: Image-Management
1. ⬜ ImageApi.list() implementieren
2. ⬜ ImageApi.pull() ohne Progress implementieren
3. ⬜ Progress-Tracking für Image-Pull hinzufügen
4. ⬜ ImageApi.remove() implementieren

### Phase 3: Container-Management
1. ⬜ ContainerApi.create() implementieren
2. ⬜ ContainerApi.start() implementieren
3. ⬜ ContainerApi.stop() implementieren
4. ⬜ ContainerApi.remove() implementieren
5. ⬜ ContainerApi.list() implementieren

### Phase 4: Testing & Dokumentation
1. ⬜ Unit-Tests für alle APIs
2. ⬜ Integration-Tests mit echtem Docker
3. ⬜ README mit Verwendungsbeispielen
4. ⬜ API-Dokumentation

## Technische Herausforderungen

1. **Unix Socket Support**: Ktor unterstützt möglicherweise keine Unix-Sockets direkt
   - **Lösung**: Custom HTTP-Client-Engine oder cURL via cinterop
   
2. **JSON Streaming**: Progress-Updates kommen als JSON-Stream
   - **Lösung**: Zeilenweises Parsen des Response-Bodies
   
3. **Kotlin/Native Einschränkungen**: Nicht alle JVM-Bibliotheken verfügbar
   - **Lösung**: Native-kompatible Bibliotheken verwenden (Ktor, kotlinx.serialization)

4. **Error Handling**: Docker-API gibt verschiedene Error-Codes zurück
   - **Lösung**: Robustes Exception-Handling mit spezifischen Exception-Typen

## Dependencies

Zusätzlich zu den bereits vorhandenen:
- `ktor-client-curl` (für Unix-Socket-Support)
- Eventuell `kotlinx-datetime` für Zeitstempel-Handling

## Lizenz und Dokumentation

- Code-Dokumentation in KDoc-Format
- Beispiele im README
- MIT oder Apache 2.0 Lizenz (zu entscheiden)
