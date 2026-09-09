# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first so rebuilds are fast.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ffmpeg for the media pipeline (transcoding + video posters),
# fonts for headless AWT image rendering, curl for the healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for the application.
RUN groupadd -r albumy && useradd -r -g albumy albumy \
    && mkdir -p /app/uploads && chown -R albumy:albumy /app

COPY --from=build /app/target/albumy.jar /app/app.jar

USER albumy
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]