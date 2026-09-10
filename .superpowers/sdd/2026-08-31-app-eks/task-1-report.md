# Task 1 report - released dependency build

## Result

- Pinned Kotlin JVM and Spring plugins to `2.4.10`, Spring Boot to `4.1.1`, and Springdoc to `3.1.0`.
- Removed Spring snapshot repositories and changed the project version from `0.0.1-SNAPSHOT` to `0.0.1`, so the policy test does not accept a snapshot project artifact.
- Preserved the Java 17 toolchain and the existing package-level JaCoCo minimum of 80% for domain and application packages.
- Changed the Docker build stage to run `./gradlew --no-daemon check bootJar` with no test bypass.
- Added synthetic PostgreSQL services to both CI jobs that build the Dockerfile and mapped `host.docker.internal` to the build host. The Docker build arguments configure only the build-stage test database URL and username; no credential or AWS secret was read.
- Added the requested `ReleasedDependenciesTest` and excluded local verification toolchains/caches from the Docker context.

## Released dependency evidence

Checked on 2026-09-08 before selecting versions:

- Spring Boot Gradle plugin 4.1.1: https://plugins.gradle.org/plugin/org.springframework.boot/4.1.1 (published 2026-08-20).
- Kotlin JVM Gradle plugin 2.4.10: https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm/2.4.10 (published 2026-07-14).
- Maven Central Springdoc BOM 3.1.0 includes `springdoc-openapi-starter-webmvc-ui` at 3.1.0: https://central.sonatype.com/artifact/org.springdoc/springdoc-openapi-bom/3.1.0.

The requested versions were all released and available; no replacement was necessary.

## Baseline and verification evidence

The host had only JDK 21, so an official Eclipse Temurin JDK 17 archive was downloaded into ignored worktree scratch and used through `JAVA_HOME`. `GRADLE_USER_HOME` was also placed in ignored worktree scratch. Docker and Gradle used only a synthetic local PostgreSQL fixture (`oficina-phase3-test-db`, host port 15432); no AWS credential or secret value was accessed.

1. Red test:
   `./gradlew test --tests '*ReleasedDependenciesTest'`
   failed as expected at `ReleasedDependenciesTest.kt:13` because the original build contained `SNAPSHOT`.
2. Focused green test after the pins:
   `./gradlew test --tests '*ReleasedDependenciesTest'`
   passed (`BUILD SUCCESSFUL in 54s`, 7 actionable tasks).
3. Complete backend gate on a clean synthetic database:
   `./gradlew --no-daemon clean check bootJar`
   passed (`BUILD SUCCESSFUL in 38s`, 11 actionable tasks). This executed tests, `jacocoTestCoverageVerification`, `check`, and `bootJar` under Java 17.
4. Docker gate on a fresh synthetic database:
   `docker build --add-host host.docker.internal=host-gateway --build-arg BUILD_TEST_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:15432/oficina_docker -t oficina-task1-verify -f Dockerfile .`
   passed (`BUILD SUCCESSFUL in 1m 38s`, 10 actionable Gradle tasks) and exported image `oficina-task1-verify:latest`. The Docker build log shows `test`, `jacocoTestCoverageVerification`, `check`, and `bootJar` executed inside the build stage.
5. Static checks:
   `git diff --check` passed. Searches found no `SNAPSHOT` or `repo.spring.io/snapshot` in the build/settings files and found the exact required Gradle gate in Dockerfile and CI.

## Concern for later cleanup

The integration suite mutates persistent database state. Reusing the database from the complete local run caused two integration flows to fail in a first Docker attempt; the Docker gate passed against a fresh database, matching GitHub Actions service-container behavior. Test isolation belongs to the later test cleanup task and was not expanded into this build-only task.
