FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/job-hunter-1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /app/data /app/logs

ENTRYPOINT ["java", "-jar", "app.jar", "/app/config.yml"]
