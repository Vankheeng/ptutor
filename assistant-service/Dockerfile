# assistant-service (thêm mới cho demo trợ lý). Build context = thư mục gốc repo, vì dịch vụ đọc
# knowledge-base/ và tool-catalog/ nằm ngoài thư mục của nó:
#   docker compose --profile assistant build assistant-service
FROM python:3.12-slim AS deps
WORKDIR /srv/assistant-service
COPY assistant-service/requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PYTHONIOENCODING=utf-8 \
    ASSISTANT_ROUTES_FILE=/srv/knowledge-base/_routes.yaml \
    ASSISTANT_MEMORY_KB_PATH=/srv/knowledge-base \
    ASSISTANT_TOOL_CATALOG_PATH=/srv/tool-catalog/tools.yaml \
    ASSISTANT_TOOL_FIXTURES_DIR=/srv/assistant-service/eval/fixtures/scenarios
COPY --from=deps /install /usr/local
RUN useradd --system --uid 10002 assistant
WORKDIR /srv/assistant-service
COPY knowledge-base /srv/knowledge-base
COPY tool-catalog /srv/tool-catalog
COPY assistant-service/app app
COPY assistant-service/migrations migrations
COPY assistant-service/alembic.ini assistant-service/docker-entrypoint.sh ./
COPY assistant-service/eval/__init__.py assistant-service/eval/scripted_policy.py eval/
COPY assistant-service/eval/fixtures eval/fixtures
RUN chmod +x docker-entrypoint.sh
USER assistant
EXPOSE 8100
ENTRYPOINT ["./docker-entrypoint.sh"]
