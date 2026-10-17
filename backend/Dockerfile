FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates ca-certificates-java libreoffice-writer libreoffice-calc libreoffice-impress tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng tesseract-ocr-fra poppler-utils fonts-wqy-microhei fonts-noto-core fonts-noto-cjk \
    && update-ca-certificates -f \
    && test -s /etc/ssl/certs/java/cacerts \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir -p /app/data/files && chown -R 10001:10001 /app
COPY --from=build /app/target/doc-assistant-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-Xmx768m","-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts","-Dcom.sun.security.enableAIAcaIssuers=true","-jar","app.jar"]
