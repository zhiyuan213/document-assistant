FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-writer fonts-wqy-microhei && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir -p /app/data/files && chown -R 10001:10001 /app
COPY --from=build /app/target/doc-assistant-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-Xmx768m","-jar","app.jar"]
