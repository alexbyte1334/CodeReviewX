FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 codereviewx \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/codereviewx codereviewx \
    && install -d -m 0700 -o codereviewx -g codereviewx \
        /home/codereviewx /home/codereviewx/.config /home/codereviewx/.config/jgit \
        /var/lib/codereviewx/rag-work
COPY --from=build /workspace/target/*.jar /app/app.jar
ENV HOME=/home/codereviewx
USER codereviewx
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
