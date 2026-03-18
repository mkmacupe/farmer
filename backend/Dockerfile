FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Pre-download dependencies to leverage Docker cache
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
# Keep this file in sync with backend-root changes so Render detects monorepo deploys.
COPY --from=build /app/target/sales-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
