# --- Stage 1: Build the JAR ---

FROM maven:3-eclipse-temurin-25 AS builder

# Tests require sqlite3
# RUN apt-get update && apt-get install -y --no-install-recommends sqlite3

WORKDIR /build

COPY src src
COPY pom.xml .

# Package app into /target
# RUN mvn package
RUN mvn package -DskipTests

# --- Stage 2: Small Runtime Container ---

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache sqlite

WORKDIR /app

COPY --from=builder /build/target/outtamny-*.jar ./outtamny.jar

COPY --chmod=755 entrypoint.sh .

ENTRYPOINT ["/app/entrypoint.sh"]
