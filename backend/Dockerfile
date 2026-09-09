FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY spring-data-graphdb ./spring-data-graphdb
COPY graphdb-demo ./graphdb-demo
RUN mvn -B clean verify
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/graphdb-demo/target/graphdb-demo-0.1.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
