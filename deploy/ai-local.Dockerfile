# Offline refresh of AI service code; the regular ai-service/Dockerfile is the portable build.
# Build context: ai-service
FROM ptutor-ai-service:latest
COPY app /srv/app
COPY scripts /srv/scripts
