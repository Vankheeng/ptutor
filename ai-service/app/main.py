"""Stateless AI service: text in, vectors, relevance scores or extracted tags out. Only the backend calls it, over the internal network."""

import hmac
import logging
import threading
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException

from .config import EMBED_MODEL_VERSION, RERANK_MODEL_VERSION, Settings, load_settings
from .model_store import download_embed_files, download_rerank_files, missing_embed_files, missing_rerank_files
from .llm import LlmBackend, LlmUnavailable, OpenAiBackend, enrich, parse_query, suggest_tags
from .schemas import (
    EmbedRequest,
    EmbedResponse,
    EnrichRequest,
    EnrichResponse,
    HealthResponse,
    ParseQueryRequest,
    ParseQueryResponse,
    RerankRequest,
    RerankResponse,
    SuggestTagsRequest,
    SuggestTagsResponse,
)

log = logging.getLogger("ai-service")


class ModelState:
    def __init__(self):
        self.embedder = None
        self.error: str | None = None
        self.reranker = None
        self.rerank_enabled = True
        self.rerank_error: str | None = None

    @property
    def status(self) -> str:
        if self.embedder is not None:
            return "ready"
        return "error" if self.error else "loading"

    @property
    def rerank_status(self) -> str:
        if self.reranker is not None:
            return "ready"
        if not self.rerank_enabled:
            return "disabled"
        return "error" if self.rerank_error else "loading"


def _load_models(settings: Settings, state: ModelState) -> None:
    try:
        missing = missing_embed_files(settings.embed_dir)
        if missing and settings.auto_download:
            log.info("Downloading embedding model files to %s", settings.embed_dir)
            download_embed_files(settings.embed_dir)
            missing = missing_embed_files(settings.embed_dir)
        if missing:
            raise FileNotFoundError(
                f"Missing model files in {settings.embed_dir}: {', '.join(missing)}. "
                "Run scripts/download_models.py or set AI_AUTO_DOWNLOAD=true."
            )
        from .embed import Embedder

        state.embedder = Embedder(settings.embed_dir, settings.embed_max_tokens, settings.threads)
        log.info("Embedding model %s is ready", EMBED_MODEL_VERSION)
    except Exception as exception:  # the service stays up and reports the problem on /health
        state.error = str(exception)
        log.exception("Unable to load the embedding model")
    if not settings.rerank_enabled:
        return
    try:  # after the embedder: search works without reranking, not without vectors
        missing = missing_rerank_files(settings.rerank_dir)
        if missing and settings.auto_download:
            log.info("Downloading reranking model files to %s", settings.rerank_dir)
            download_rerank_files(settings.rerank_dir)
            missing = missing_rerank_files(settings.rerank_dir)
        if missing:
            raise FileNotFoundError(
                f"Missing model files in {settings.rerank_dir}: {', '.join(missing)}. "
                "Run scripts/download_models.py or set AI_AUTO_DOWNLOAD=true."
            )
        from .rerank import Reranker

        state.reranker = Reranker(
            settings.rerank_dir, settings.rerank_max_tokens, settings.threads, settings.rerank_batch_size
        )
        log.info("Reranking model %s is ready", RERANK_MODEL_VERSION)
    except Exception as exception:
        state.rerank_error = str(exception)
        log.exception("Unable to load the reranking model")


def create_app(
    settings: Settings | None = None, embedder=None, llm: LlmBackend | None = None, reranker=None
) -> FastAPI:
    """`embedder`, `llm` and `reranker` let tests inject fakes instead of loading a model or calling a provider."""
    settings = settings or load_settings()
    if not settings.internal_token and not settings.allow_anonymous:
        raise RuntimeError("AI_INTERNAL_TOKEN must be set (or AI_ALLOW_ANONYMOUS=true for local experiments)")
    state = ModelState()
    state.embedder = embedder
    state.reranker = reranker
    state.rerank_enabled = settings.rerank_enabled
    # Tests inject one fake for everything; in production the interactive endpoint gets its own settings.
    interactive_llm = llm
    if llm is None and settings.openai_api_key:
        llm = OpenAiBackend(
            settings.openai_api_key, settings.llm_model, settings.llm_timeout_seconds, settings.llm_reasoning_effort
        )
        interactive_llm = OpenAiBackend(
            settings.openai_api_key, settings.llm_model, settings.parse_timeout_seconds,
            settings.parse_reasoning_effort,
        )
    if llm is None:
        log.warning("OPENAI_API_KEY is not set: /enrich is disabled, embedding still works")

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if state.embedder is None:
            # Loading takes a few seconds; do it in the background so /health answers "loading".
            threading.Thread(target=_load_models, args=(settings, state), daemon=True).start()
        yield

    app = FastAPI(title="PTutor AI service", lifespan=lifespan)
    app.state.models = state

    def require_token(x_internal_token: str | None = Header(default=None)) -> None:
        if not settings.internal_token:
            return
        if x_internal_token is None or not hmac.compare_digest(x_internal_token, settings.internal_token):
            raise HTTPException(status_code=401, detail="Invalid internal token")

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(
            status=state.status,
            embed_model=EMBED_MODEL_VERSION,
            llm="ready" if llm is not None else "disabled",
            llm_model=llm.model if llm is not None else None,
            rerank=state.rerank_status,
            rerank_model=RERANK_MODEL_VERSION if state.rerank_enabled else None,
            detail=state.error or state.rerank_error,
        )

    @app.post("/embed", response_model=EmbedResponse, dependencies=[Depends(require_token)])
    def embed(request: EmbedRequest) -> EmbedResponse:
        if state.embedder is None:
            raise HTTPException(status_code=503, detail=f"Embedding model is {state.status}")
        if len(request.texts) > settings.embed_max_batch:
            raise HTTPException(status_code=422, detail=f"At most {settings.embed_max_batch} texts per request")
        if any(not text.strip() for text in request.texts):
            raise HTTPException(status_code=422, detail="Texts must not be blank")
        if any(len(text) > settings.embed_max_chars for text in request.texts):
            raise HTTPException(
                status_code=422, detail=f"Each text must be at most {settings.embed_max_chars} characters"
            )
        vectors = state.embedder.embed(request.texts)
        return EmbedResponse(
            model=state.embedder.model_version,
            dimensions=state.embedder.dimensions,
            embeddings=vectors.tolist(),
        )

    @app.post("/rerank", response_model=RerankResponse, dependencies=[Depends(require_token)])
    def rerank(request: RerankRequest) -> RerankResponse:
        if state.reranker is None:
            raise HTTPException(status_code=503, detail=f"Reranking model is {state.rerank_status}")
        if len(request.documents) > settings.rerank_max_documents:
            raise HTTPException(
                status_code=422, detail=f"At most {settings.rerank_max_documents} documents per request"
            )
        texts = [request.query, *request.documents]
        if any(not text.strip() for text in texts):
            raise HTTPException(status_code=422, detail="Texts must not be blank")
        if any(len(text) > settings.rerank_max_chars for text in texts):
            raise HTTPException(
                status_code=422, detail=f"Each text must be at most {settings.rerank_max_chars} characters"
            )
        scores = state.reranker.score(request.query, request.documents)
        return RerankResponse(model=state.reranker.model_version, scores=scores)

    @app.post("/enrich", response_model=EnrichResponse, dependencies=[Depends(require_token)])
    def enrich_entity(request: EnrichRequest) -> EnrichResponse:
        if llm is None:
            raise HTTPException(status_code=503, detail="LLM is not configured")
        if sum(len(value) for value in request.fields.values()) > settings.enrich_max_chars:
            raise HTTPException(
                status_code=422, detail=f"Fields must not exceed {settings.enrich_max_chars} characters"
            )
        try:
            return enrich(llm, request)
        except LlmUnavailable as error:
            # The user text is never logged; the message only carries the provider error.
            log.warning("LLM extraction failed: %s", error)
            raise HTTPException(status_code=502, detail="LLM provider failed") from error

    @app.post("/parse-query", response_model=ParseQueryResponse, dependencies=[Depends(require_token)])
    def parse_search_query(request: ParseQueryRequest) -> ParseQueryResponse:
        if interactive_llm is None:
            raise HTTPException(status_code=503, detail="LLM is not configured")
        if not request.text.strip():
            raise HTTPException(status_code=422, detail="Text must not be blank")
        try:
            return parse_query(interactive_llm, request)
        except LlmUnavailable as error:
            # The sentence itself is never logged; the message only carries the provider error.
            log.warning("LLM query parsing failed: %s", error)
            raise HTTPException(status_code=502, detail="LLM provider failed") from error

    @app.post("/suggest-tags", response_model=SuggestTagsResponse, dependencies=[Depends(require_token)])
    def suggest_subject_tags(request: SuggestTagsRequest) -> SuggestTagsResponse:
        if llm is None:
            raise HTTPException(status_code=503, detail="LLM is not configured")
        if sum(len(sample) for sample in request.samples) > settings.enrich_max_chars:
            raise HTTPException(
                status_code=422, detail=f"Samples must not exceed {settings.enrich_max_chars} characters"
            )
        try:
            return suggest_tags(llm, request)
        except LlmUnavailable as error:
            log.warning("LLM tag suggestion failed: %s", error)
            raise HTTPException(status_code=502, detail="LLM provider failed") from error

    return app
