@file:OptIn(ExperimentalTime::class)

import api.image.ImagePullStatus
import docker.DockerClient
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

fun main() = runBlocking {
    val client = DockerClient()

    client.use { client ->

//        val numbers = (1..5).toList()
//        numbers.forEach { println("Number: $it") }
//
//        delay(1.seconds)
//
//        repeat(10) {
//            numbers.forEachIndexed { index, value ->
//                // Cursor nach oben bewegen
//                print("\u001B[${numbers.size - index}A")
//                // Linie löschen
//                print("\u001B[2K\r")
//                // Zahl hochzählen
//                // Neue Zahl ausgeben
//                println("Number: ${numbers[index] + it}")
//                // Cursor wieder nach unten bewegen
//                print("\u001B[${numbers.size - index - 1}B")
//                delay(200)
//            }
//        }
//
//        println(client.getInfo())

//        val images = client.images.getImages()
//        val table = buildTable {
//            row {
//                cell {
//                    +"ID"
//                }
//                cell {
//                    +"TAGS"
//                }
//                cell {
//                    +"CREATED"
//                }
//                cell {
//                    +"SIZE"
//                }
//            }
//            images.forEach { image ->
//                row {
//                    cell {
//                        +image.id.substringAfter("sha256:").take(12)
//                    }
//                    cell {
//                        +image.repoTags.joinToString()
//                    }
//                    cell {
//                        +Instant.fromEpochSeconds(image.created).toString()
//                    }
//                    cell {
//                        +image.size.toString()
//                    }
//                }
//            }
//        }
//
//        println(table)

        val layerHashes = mutableListOf<String>()
        client.images.pull(
            image = "gitlab/gitlab-ce",
            beforeDownload = { hashes ->
                hashes.forEach { layerHash ->
                    println("$layerHash Download wird vorbereitet...")
                }
                layerHashes.addAll(hashes)
            },
            onDownload = { layerHash, status ->
//                return@pull
                val line = layerHashes.indexOf(layerHash)
                if (line == -1) return@pull

                // Cursor nach oben bewegen zur richtigen Zeile
                print("\u001B[${layerHashes.size - line}A")
                // Linie löschen und Cursor an den Zeilenanfang
                print("\u001B[2K\r")
                // Status aktualisieren
                print("$layerHash ")
                when (status) {
                    is ImagePullStatus.Pulling -> {
                        val percent = if (status.bytesTotal > 0) {
                            (status.bytesPulled * 100f / status.bytesTotal)
                        } else {
                            0f
                        }

                        buildString {
                            append("Downloading ")
                            // round to one decimal place and pad with spaces
                            append(percent.roundToInt().toString().padStart(3, ' '))
                            append(".")

                            val percentOne = (percent * 10f).roundToInt();
                            append((percentOne % 10).toString())

                            append("%")
                            append(": ")
                            append(status.bytesPulled.toString().padStart(10, ' '))
                            append("/${status.bytesTotal}")
                        }
                    }
                    ImagePullStatus.Downloaded -> {
                        "Download complete"
                    }

                    is ImagePullStatus.Extracting -> {
                        buildString {
                            append("Extracting (")
                            append(status.current)
                            append(status.unit)
                            append(")")
                        }
                    }
                }.let { println(it) }
                // Cursor wieder nach unten zum ursprünglichen Ende
                print("\u001B[${layerHashes.size - line - 1}B")
            }

        )
    }

    println("Goodbye!")
}
