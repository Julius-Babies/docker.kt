package es.jvbabi.docker.kt.api.container

enum class ContainerState(val value: String) {
    CREATED("created"),
    RUNNING("running"),
    PAUSED("paused"),
    RESTARTING("restarting"),
    REMOVING("removing"),
    EXITED("exited"),
    DEAD("dead");

    companion object {
        fun fromString(value: String): ContainerState? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}

