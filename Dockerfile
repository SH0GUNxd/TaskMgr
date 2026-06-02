FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY src/ ./src/

RUN mkdir -p out && \
    javac -d out src/*.java && \
    jar --create --file TaskManager.jar --main-class taskmanager.Main -C out .

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/TaskManager.jar .
COPY web/ ./web/

EXPOSE 8080

CMD ["java", "-jar", "TaskManager.jar", "--web", "8080"]