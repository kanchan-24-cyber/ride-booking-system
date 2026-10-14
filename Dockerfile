# Build stage: compile the Java sources
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src src
RUN javac -d out $(find src/main -name "*.java")

# Run stage: small JRE image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out out
COPY web web
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "out", "com.ridebooking.web.WebServer"]
