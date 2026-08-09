# Docker.kt - Kotlin Native Docker Client

A Kotlin/Native library for interacting with Docker through Unix domain sockets. Pure Kotlin Native implementation using C-interop, no JVM dependencies.

## Tests

The suite is Kotest and splits in two. Most specs are integration tests that talk to a real Docker daemon and are tagged `RequiresDocker`; the rest are plain unit tests over the parsing helpers and need nothing at all.

Native test binaries only run on a matching host, so use the test task of your host target:

```bash
./gradlew :docker-kt:macosArm64Test   # macOS (Apple Silicon)
./gradlew :docker-kt:linuxX64Test     # Linux (x86_64)
./gradlew allTests                    # everything runnable on the current host
```

Drop the tag to run the unit tests alone — no daemon, and it finishes in under a second:

```bash
KOTEST_TAGS='!RequiresDocker' ./gradlew :docker-kt:macosArm64Test
```

### Layout

Specs live next to the API area they cover, one package each:

```
src/nativeTest/kotlin/es/jvbabi/docker/kt/
├── support/    shared fixtures: the RequiresDocker tag, run-scoped resource names, teardown helpers
├── container/  ContainerApi
├── image/      ImageApi
└── system/     DockerClient itself
```

A spec that needs a daemon declares `tags(RequiresDocker)`; one that does not, says so in its doc comment and stays untagged.

Two rules keep the integration specs reusable on a real development machine:

- **Everything is scoped to the run.** Containers, volumes, networks and temp directories are named through `testResourceName(...)`, which appends a per-run id. Nothing is ever addressed by a fixed name.
- **`afterSpec` restores the host**, and runs its teardown through the `…Quietly` helpers so a cleanup error cannot mask the failure that caused it. Where a resource is global and cannot be run-scoped — an image tag, for instance — the spec puts back what it found.

Specs have to live in a package. Kotest matches Gradle's `--tests` filter against `<package>.<Spec>`, so specs in the default package can never be matched — which makes the IDE gutter icons fail with "No matching tests found".
