# Build with the repository root as context, because the jar packages /kit:
#   docker build -f backend/Dockerfile -t dockmind-backend .
# Only the paths named below enter the image, so .env and /mobile never do.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY kit/seed.sql kit/seed.sql
COPY kit/fixtures kit/fixtures
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
WORKDIR /src/backend
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --create-home --uid 10001 dockmind \
    && mkdir -p /app/data \
    && chown -R dockmind:dockmind /app
WORKDIR /app
COPY --from=build /src/backend/target/*.jar app.jar
USER dockmind

# Sized for a 512 MB container. Settings and keys come from the host's environment variables.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
