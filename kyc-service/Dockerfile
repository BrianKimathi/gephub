# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# Ensure Maven wrapper is executable
RUN chmod +x mvnw \
    && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
ENV JAVA_OPTS="-Xms256m -Xmx512m"
WORKDIR /app
COPY --from=build /workspace/target/kyc-service-0.0.1-SNAPSHOT.jar app.jar
VOLUME ["/var/lib/gephub/kyc-media"]
EXPOSE 8081
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]


