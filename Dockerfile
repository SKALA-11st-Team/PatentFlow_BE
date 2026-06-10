FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
# INFRA-13: 의존성 레이어를 소스와 분리해 캐싱한다 — pom.xml이 바뀌지 않으면 의존성 다운로드 레이어를
# 재사용해 소스만 변경된 빌드가 매번 의존성을 다시 받지 않는다.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
COPY docs ./docs
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
COPY --from=build /workspace/docs ./docs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
