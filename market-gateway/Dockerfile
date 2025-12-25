FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
# Skip tests to speed up the build
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
