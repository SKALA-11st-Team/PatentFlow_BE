FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
COPY --from=build /workspace/docs ./docs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
