FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY jaeger-mcp-service/build.gradle ./jaeger-mcp-service/build.gradle
RUN ./gradlew :jaeger-mcp-service:dependencies --no-daemon -q

COPY jaeger-mcp-service/src ./jaeger-mcp-service/src
RUN ./gradlew :jaeger-mcp-service:bootJar --no-daemon -q

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/jaeger-mcp-service/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
