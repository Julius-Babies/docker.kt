package es.jvbabi.docker.kt.api.container.functions

import es.jvbabi.docker.kt.api.container.CommandResult
import es.jvbabi.docker.kt.docker.DockerClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal suspend fun runCommandInternalSimple(
    dockerClient: DockerClient,
    containerId: String,
    command: List<String>,
    environment: Map<String, String>,
): CommandResult {
    val createExecInstanceResponse = dockerClient.socket.post("/containers/$containerId/exec") {
        contentType(ContentType.Application.Json)
        setBody(CreateExecInstanceRequest(
            attachStdout = true,
            attachStderr = true,
            cmd = command,
            env = environment.map { "${it.key}=${it.value}" }
        ))
    }
    if (!createExecInstanceResponse.status.isSuccess()) throw RuntimeException("Failed to run command: ${createExecInstanceResponse.status.value} ${createExecInstanceResponse.bodyAsText()}")
    val execId = createExecInstanceResponse.body<CreateExecInstanceResponse>().id

    val stdout = StringBuilder()

    dockerClient.socket.preparePost("/exec/$execId/start") {
        contentType(ContentType.Application.Json)
        setBody(StartExecInstanceResponse(detach = false))
    }.execute { response ->
        val status = response.status
        if (!status.isSuccess()) throw RuntimeException("Failed to run command: ${status.value} ${response.bodyAsText()}")

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            if (channel.availableForRead < 8) {
                channel.awaitContent()
                if (channel.availableForRead < 8) break
            }

            val header = ByteArray(8)
            channel.readFully(header, 0, 8)

            val streamType = StreamType.fromByte(header[0])
            val payloadSize = ((header[4].toInt() and 0xFF) shl 24) or
                    ((header[5].toInt() and 0xFF) shl 16) or
                    ((header[6].toInt() and 0xFF) shl 8) or
                    (header[7].toInt() and 0xFF)

            val payload = ByteArray(payloadSize)
            channel.readFully(payload, 0, payloadSize)
            val text = payload.decodeToString()

            when (streamType) {
                StreamType.STDOUT -> stdout.append(text)
                else -> {}
            }
        }
    }

    val exitResponse = dockerClient.socket.get("/exec/$execId/json")
    if (!exitResponse.status.isSuccess()) throw RuntimeException("Failed to run command: ${exitResponse.status.value} ${exitResponse.bodyAsText()}")
    val exitCode = exitResponse.body<ExecJsonResponse>().exitCode

    return CommandResult(exitCode, stdout.toString())
}

// New streaming variant
internal fun runCommandInternalFlow(
    dockerClient: DockerClient,
    containerId: String,
    command: List<String>,
    environment: Map<String, String>,
): es.jvbabi.docker.kt.api.container.CommandStreamResult {
    // Channels so we can close them when the command finishes and the returned flows complete.
    val stdoutChannel = Channel<String>(capacity = 16)
    val stderrChannel = Channel<String>(capacity = 16)
    val exitDeferred: CompletableDeferred<Int> = CompletableDeferred()

    // Start a coroutine in background to run the command and emit into channels.
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val createExecInstanceResponse = dockerClient.socket.post("/containers/$containerId/exec") {
                contentType(ContentType.Application.Json)
                setBody(CreateExecInstanceRequest(
                    attachStdout = true,
                    attachStderr = true,
                    cmd = command,
                    env = environment.map { "${it.key}=${it.value}" }
                ))
            }
            if (!createExecInstanceResponse.status.isSuccess()) {
                // emit error and close channels
                stderrChannel.send("Failed to create exec instance: ${createExecInstanceResponse.status.value} ${createExecInstanceResponse.bodyAsText()}")
                exitDeferred.completeExceptionally(RuntimeException("Failed to run command: ${createExecInstanceResponse.status.value} ${createExecInstanceResponse.bodyAsText()}"))
                stdoutChannel.close()
                stderrChannel.close()
                return@launch
            }
            val execId = createExecInstanceResponse.body<CreateExecInstanceResponse>().id

            dockerClient.socket.preparePost("/exec/$execId/start") {
                contentType(ContentType.Application.Json)
                setBody(StartExecInstanceResponse(detach = false))
            }.execute { response ->
                val status = response.status
                if (!status.isSuccess()) {
                    stderrChannel.send("Failed to start exec: ${status.value} ${response.bodyAsText()}")
                    exitDeferred.completeExceptionally(RuntimeException("Failed to run command: ${status.value} ${response.bodyAsText()}"))
                    stdoutChannel.close()
                    stderrChannel.close()
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    if (channel.availableForRead < 8) {
                        channel.awaitContent()
                        if (channel.availableForRead < 8) break
                    }

                    val header = ByteArray(8)
                    channel.readFully(header, 0, 8)

                    val streamType = StreamType.fromByte(header[0])
                    val payloadSize = ((header[4].toInt() and 0xFF) shl 24) or
                            ((header[5].toInt() and 0xFF) shl 16) or
                            ((header[6].toInt() and 0xFF) shl 8) or
                            (header[7].toInt() and 0xFF)

                    val payload = ByteArray(payloadSize)
                    channel.readFully(payload, 0, payloadSize)
                    val text = payload.decodeToString()

                    when (streamType) {
                        StreamType.STDOUT -> stdoutChannel.send(text)
                        StreamType.STDERR -> stderrChannel.send(text)
                        else -> {}
                    }
                }
            }

            val exitResponse = dockerClient.socket.get("/exec/$execId/json")
            if (!exitResponse.status.isSuccess()) {
                val msg = "Failed to get exec result: ${exitResponse.status.value} ${exitResponse.bodyAsText()}"
                stderrChannel.send(msg)
                exitDeferred.completeExceptionally(RuntimeException(msg))
            } else {
                val exitCode = exitResponse.body<ExecJsonResponse>().exitCode
                exitDeferred.complete(exitCode)
            }
        } catch (t: Throwable) {
            // attempt to notify error consumers
            try {
                stderrChannel.send("Exception: ${t.message}")
            } catch (_: Throwable) {}
            if (!exitDeferred.isCompleted) exitDeferred.completeExceptionally(t)
        } finally {
            // close channels so receiveAsFlow() completes
            stdoutChannel.close()
            stderrChannel.close()
        }
    }

    return es.jvbabi.docker.kt.api.container.CommandStreamResult(
        stdout = stdoutChannel.receiveAsFlow(),
        stderr = stderrChannel.receiveAsFlow(),
        exitCode = exitDeferred as Deferred<Int>
    )
}

@Serializable
data class CreateExecInstanceRequest(
    @SerialName("AttachStdout") val attachStdout: Boolean,
    @SerialName("AttachStderr") val attachStderr: Boolean,
    @SerialName("Cmd") val cmd: List<String>,
    @SerialName("Env") val env: List<String>,
)

@Serializable
data class StartExecInstanceResponse(
    @SerialName("Detach") val detach: Boolean
)

@Serializable
data class CreateExecInstanceResponse(
    @SerialName("Id") val id: String
)

@Serializable
data class ExecJsonResponse(
    @SerialName("ExitCode") val exitCode: Int
)

enum class StreamType {
    STDIN, STDOUT, STDERR;

    companion object {
        fun fromByte(byte: Byte): StreamType = when(byte.toInt()) {
            1 -> STDOUT
            2 -> STDERR
            3 -> STDIN
            else -> throw IllegalArgumentException("Unknown stream type: $byte")
        }
    }
}