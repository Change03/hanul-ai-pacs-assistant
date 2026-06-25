FROM gradle:8.10.2-jdk21 AS build

WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar
COPY models ./models

EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
