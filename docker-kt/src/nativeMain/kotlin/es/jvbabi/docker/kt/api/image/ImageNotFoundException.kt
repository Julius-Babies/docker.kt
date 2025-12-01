package es.jvbabi.docker.kt.api.image

class ImageNotFoundException(val image: String): Exception("Image not found: $image")

