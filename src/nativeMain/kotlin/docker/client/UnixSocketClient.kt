package docker.client

import docker.exceptions.DockerException
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Low-level Unix domain socket client using C-interop.
 * Provides socket operations for communication with Docker daemon.
 */
class UnixSocketClient(private val socketPath: String) {
    private var socketFd: Int = -1
    
    /**
     * Connects to the Unix domain socket.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun connect() {
        // Create socket
        socketFd = socket(AF_UNIX, SOCK_STREAM, 0)
        if (socketFd < 0) {
            throw DockerException("Failed to create socket: ${strerror(errno)?.toKString()}")
        }
        
        memScoped {
            val addr = alloc<sockaddr_un>()
            addr.sun_family = AF_UNIX.convert()
            
            // Copy socket path to sun_path
            socketPath.encodeToByteArray().usePinned { pinned ->
                val maxLen = 108 // Standard size of sun_path
                val pathLen = minOf(socketPath.length, maxLen - 1)
                
                for (i in 0 until pathLen) {
                    addr.sun_path[i] = pinned.get()[i].toByte()
                }
                addr.sun_path[pathLen] = 0 // Null terminator
            }
            
            // Connect to socket
            val result = connect(
                socketFd,
                addr.ptr.reinterpret(),
                sizeOf<sockaddr_un>().convert()
            )
            
            if (result < 0) {
                close(socketFd)
                socketFd = -1
                throw DockerException("Failed to connect to socket $socketPath: ${strerror(errno)?.toKString()}")
            }
        }
    }
    
    /**
     * Sends data through the socket.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun send(data: String): Int {
        if (socketFd < 0) {
            throw DockerException("Socket not connected")
        }
        
        return data.encodeToByteArray().usePinned { pinned ->
            val sent = send(socketFd, pinned.addressOf(0), data.length.convert(), 0)
            if (sent < 0) {
                throw DockerException("Failed to send data: ${strerror(errno)?.toKString()}")
            }
            sent.toInt()
        }
    }
    
    /**
     * Sends raw bytes through the socket.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun sendBytes(data: ByteArray): Int {
        if (socketFd < 0) {
            throw DockerException("Socket not connected")
        }
        
        return data.usePinned { pinned ->
            val sent = send(socketFd, pinned.addressOf(0), data.size.convert(), 0)
            if (sent < 0) {
                throw DockerException("Failed to send data: ${strerror(errno)?.toKString()}")
            }
            sent.toInt()
        }
    }
    
    /**
     * Receives data from the socket.
     * @param maxSize Maximum number of bytes to receive
     * @return Received data as ByteArray
     */
    @OptIn(ExperimentalForeignApi::class)
    fun receive(maxSize: Int = 4096): ByteArray {
        if (socketFd < 0) {
            throw DockerException("Socket not connected")
        }
        
        return memScoped {
            val buffer = allocArray<ByteVar>(maxSize)
            val received = recv(socketFd, buffer, maxSize.convert(), 0)
            
            if (received < 0) {
                throw DockerException("Failed to receive data: ${strerror(errno)?.toKString()}")
            }
            
            if (received == 0L) {
                // Connection closed
                return@memScoped ByteArray(0)
            }
            
            ByteArray(received.toInt()) { i ->
                buffer[i]
            }
        }
    }
    
    /**
     * Receives all available data from the socket until connection closes or timeout.
     */
    fun receiveAll(timeoutMs: Int = 5000): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var totalSize = 0
        
        // Set socket timeout
        setTimeout(timeoutMs)
        
        try {
            while (true) {
                val chunk = receive()
                if (chunk.isEmpty()) {
                    break
                }
                chunks.add(chunk)
                totalSize += chunk.size
            }
        } catch (e: DockerException) {
            // Timeout or error, return what we have
        }
        
        // Combine all chunks
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        
        return result
    }
    
    /**
     * Sets socket timeout for receive operations.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun setTimeout(timeoutMs: Int) {
        if (socketFd < 0) return
        
        memScoped {
            val tv = alloc<timeval>()
            tv.tv_sec = (timeoutMs / 1000).convert()
            tv.tv_usec = ((timeoutMs % 1000) * 1000).convert()
            
            setsockopt(
                socketFd,
                SOL_SOCKET,
                SO_RCVTIMEO,
                tv.ptr,
                sizeOf<timeval>().convert()
            )
        }
    }
    
    /**
     * Closes the socket connection.
     */
    fun close() {
        if (socketFd >= 0) {
            close(socketFd)
            socketFd = -1
        }
    }
    
    /**
     * Checks if socket is connected.
     */
    fun isConnected(): Boolean = socketFd >= 0
}
