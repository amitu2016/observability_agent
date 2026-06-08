FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY triage-agent/build.gradle ./triage-agent/build.gradle
RUN ./gradlew :triage-agent:dependencies --no-daemon -q

COPY triage-agent/src ./triage-agent/src
RUN ./gradlew :triage-agent:bootJar --no-daemon -q

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/triage-agent/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
