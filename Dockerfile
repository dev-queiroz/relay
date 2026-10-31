FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode package -DskipTests
FROM eclipse-temurin:25-jre
RUN addgroup --system relay && adduser --system --ingroup relay relay
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/relay.jar
USER relay:relay
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/relay.jar"]
