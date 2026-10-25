FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /source
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY gateway gateway
COPY protocol protocol
COPY web/dist web/dist
RUN chmod +x backend/gradlew && backend/gradlew -p gateway test bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system wallet && useradd --system --gid wallet --home-dir /app wallet
COPY --from=build --chown=wallet:wallet /source/gateway/build/libs/gateway.jar /app/gateway.jar
USER wallet
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=15 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
