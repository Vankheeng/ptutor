#!/bin/sh
# Khởi động assistant-service trong container: chạy migration Alembic (schema assistant) rồi mở API.
# Nạp kho tri thức là việc riêng, chạy một lần (xem HUONG-DAN-DEMO.md):
#   docker compose --profile assistant run --rm assistant-service kb-import [--publish] [--allow-todo]
set -e
if [ "$1" = "kb-import" ]; then
  shift
  alembic upgrade head
  exec python -m app.kb.import --path /srv/knowledge-base "$@"
fi
alembic upgrade head
exec uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8100
