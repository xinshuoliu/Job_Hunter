FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre

ARG APP_UID=1000
RUN (userdel --remove ubuntu || true) 2>/dev/null \
    && (groupdel ubuntu || true) 2>/dev/null \
    && groupadd --system --gid ${APP_UID} jobhunter \
    && useradd --system --uid ${APP_UID} --gid jobhunter --home-dir /app --shell /usr/sbin/nologin jobhunter

WORKDIR /app
COPY --from=build /build/target/job-hunter-1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data /app/logs && chown -R jobhunter:jobhunter /app

USER jobhunter

ENTRYPOINT ["java", "-jar", "app.jar", "--config", "/app/config.yml"]
CMD ["run"]
