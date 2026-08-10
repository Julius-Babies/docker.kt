package es.jvbabi.docker.kt.docker

import kotlinx.serialization.json.Json

/**
 * How the daemon's JSON is read, shared by every platform's client so they cannot drift apart.
 */
internal val dockerJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    encodeDefaults = false

    // Docker sends null for "nothing here" rather than leaving the field out, and a default alone
    // does not cover an explicit null. Coercing turns those nulls into the declared default, which
    // is why every collection in the dto package has one.
    coerceInputValues = true
}
