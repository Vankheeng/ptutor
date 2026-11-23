# Backend PTutor — multi-stage (thêm mới cho demo trợ lý, docs/support-chatbot/trien-khai/HUONG-DAN-DEMO.md).
# Build: docker compose --profile assistant build backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre
# Múi giờ JVM = giờ Việt Nam như máy dev (quyết định 2026-11-03: KHÔNG chuyển sang UTC); thời điểm do service ghi
# bằng LocalDateTime.now(clock) vẫn là UTC — xem nhãn time_zone trong tool-catalog/tools.yaml.
ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Ho_Chi_Minh" \
    SPRING_PROFILES_ACTIVE=prod
RUN useradd --system --uid 10001 ptutor
USER ptutor
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
