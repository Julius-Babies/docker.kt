# Docker.kt - Kotlin Native Docker Client

A Kotlin/Native library for interacting with Docker through Unix domain sockets. Pure Kotlin Native implementation using C-interop, no JVM dependencies.

## Tests

The suite consists of Kotest integration tests that talk to a real Docker daemon, so a running Docker instance is required.

Native test binaries only run on a matching host, so use the test task of your host target:

```bash
./gradlew :docker-kt:macosArm64Test   # macOS (Apple Silicon)
./gradlew :docker-kt:linuxX64Test     # Linux (x86_64)
./gradlew allTests                    # everything runnable on the current host
```

Specs have to live in a package (`es.jvbabi.docker.kt`). Kotest matches Gradle's `--tests` filter against `<package>.<Spec>`, so specs in the default package can never be matched — which makes the IDE gutter icons fail with "No matching tests found".
