# syntax=docker/dockerfile:1

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./backend/

ARG MODULE
RUN mvn -B -ntp -f backend/pom.xml -pl ${MODULE} -am package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

ARG MODULE
RUN addgroup -S oms && adduser -S oms -G oms
USER oms
WORKDIR /app

COPY --from=build /workspace/backend/${MODULE}/target/*.jar /app/

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java -jar $(ls /app/*.jar | grep -v original | head -1)"]
