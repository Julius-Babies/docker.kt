pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.0" apply false // Remember to change the version in libs.version.toml as well
}

rootProject.name = "docker.kt Library"
include(":docker-kt", ":playground")