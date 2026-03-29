FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -DskipTests clean package org.springframework.boot:spring-boot-maven-plugin:3.2.6:repackage

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/ai-agent-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-Xms256m", "-Xmx768m", "-XX:MaxMetaspaceSize=256m", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
