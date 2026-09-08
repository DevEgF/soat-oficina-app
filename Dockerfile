# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM gradle:9.4.0-jdk17 AS build
WORKDIR /workspace
COPY oficina/ .
ARG BUILD_TEST_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/oficina
ARG BUILD_TEST_DATASOURCE_USERNAME=oficina
RUN chmod +x gradlew \
	&& SPRING_DATASOURCE_URL="$BUILD_TEST_DATASOURCE_URL" \
	SPRING_DATASOURCE_USERNAME="$BUILD_TEST_DATASOURCE_USERNAME" \
	./gradlew --no-daemon check bootJar \
	&& cp "build/libs/$(ls build/libs | grep -v plain | grep '\.jar$' | head -n1)" /workspace/application.jar

# ---------- Runtime stage ----------
# Tag de patch fixa (evita o flutuante 17-jre) para builds reproduzíveis.
# Alpine reduz drasticamente a superfície de ataque e o tamanho da imagem frente a jammy.
FROM eclipse-temurin:17.0.13_11-jre-alpine

# wget é usado pelo HEALTHCHECK abaixo.
RUN apk add --no-cache wget

# Usuário não-root para o runtime.
RUN addgroup -g 1001 -S app \
	&& adduser -u 1001 -S -G app -h /app -D app

WORKDIR /app
COPY --from=build /workspace/application.jar app.jar
RUN chown -R app:app /app

USER app

EXPOSE 8080

# Boa cidadania em contêiner/K8s: limita heap ao percentual da RAM do cgroup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
	CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# sh -c para que $JAVA_OPTS seja expandido em tempo de execução.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
