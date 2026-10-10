# Build stage: no local Maven/JDK needed to run the app
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI (mvn verify); skipped here to keep image builds fast
RUN mvn -B -q package -DskipTests

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /app/target/url-shortener-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
