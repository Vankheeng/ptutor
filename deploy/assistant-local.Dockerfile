# Offline refresh of the assistant image from the current branch.
# The regular assistant-service/Dockerfile remains the portable source build.
FROM ptutor-demo-ref-assistant-service:latest
USER root
COPY --chown=assistant:assistant knowledge-base /srv/knowledge-base
COPY --chown=assistant:assistant tool-catalog /srv/tool-catalog
COPY --chown=assistant:assistant assistant-service/app /srv/assistant-service/app
COPY --chown=assistant:assistant assistant-service/migrations /srv/assistant-service/migrations
COPY --chown=assistant:assistant assistant-service/alembic.ini assistant-service/docker-entrypoint.sh /srv/assistant-service/
COPY --chown=assistant:assistant assistant-service/eval/__init__.py assistant-service/eval/scripted_policy.py /srv/assistant-service/eval/
COPY --chown=assistant:assistant assistant-service/eval/fixtures /srv/assistant-service/eval/fixtures
USER assistant
