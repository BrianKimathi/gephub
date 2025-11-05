# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Leverage build cache for dependencies
COPY pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
ENV JAVA_OPTS="-Xms256m -Xmx512m"
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]


