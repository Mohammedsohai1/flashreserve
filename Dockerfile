
# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 appuser

COPY --from=build /app/target/flashreserve-*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
