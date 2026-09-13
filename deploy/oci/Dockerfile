# syntax=docker/dockerfile:1
# Build context is repository root. CI must run Gradle check before this packaging step.
FROM --platform=$BUILDPLATFORM gradle:9.4.0-jdk17 AS build
WORKDIR /workspace
COPY oficina/ .
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    chmod +x gradlew && ./gradlew --no-daemon bootJar \
    && find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;
RUN curl -fsSL https://repo.maven.apache.org/maven2/com/newrelic/agent/java/newrelic-agent/9.4.0/newrelic-agent-9.4.0.jar -o /workspace/newrelic.jar \
    && echo '1f8f42d25e6565a1a7088543deeeefacdd2b028761ac94743dd1715cf7ddf5f4  /workspace/newrelic.jar' | sha256sum -c -

# Ubuntu runtime has an ARM64 build; AWS's existing Alpine digest is untouched.
FROM eclipse-temurin:17.0.20_8-jre-noble@sha256:76bf6d763e84e4d286d5ee3df61e4adeca214977f83c7d8d2327b93db582dd9d
RUN groupadd --gid 1001 app && useradd --uid 1001 --gid 1001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=1001:1001 /workspace/app.jar /app/app.jar
COPY --from=build --chown=1001:1001 /workspace/newrelic.jar /app/newrelic.jar
USER 1001:1001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseContainerSupport"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
