FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && \
    ./gradlew clean bootJar --no-daemon && \
    jar_file="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" && \
    cp "$jar_file" app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
