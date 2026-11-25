# --- Compilación ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Ejecución ---
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 sensonet
WORKDIR /app
COPY --from=build /src/target/sensonet-backend.jar app.jar
USER sensonet
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
