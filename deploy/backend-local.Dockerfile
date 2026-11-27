# Build from the local JAR produced by the backend source with Flyway V54.
# docker build -f deploy/backend-local.Dockerfile -t ptutor-local-backend:latest backend/target
FROM ptutor-demo-ref-backend:latest
COPY backend-0.0.1-SNAPSHOT.jar /app/app.jar
