"""Runtime settings, read once from environment variables."""

import os
from dataclasses import dataclass
from pathlib import Path

# Where the files come from and how they are named on disk. Shared with scripts/download_models.py.
EMBED_REPO_ID = "Xenova/bge-m3"
EMBED_REPO_FILES = (
    "onnx/model_int8.onnx",
    "tokenizer.json",
    "tokenizer_config.json",
    "special_tokens_map.json",
    "config.json",
)
EMBED_MODEL_FILE = "onnx/model_int8.onnx"
EMBED_SUBDIR = "bge-m3"
# Stored next to every vector (embedding_model_version, at most 50 characters).
EMBED_MODEL_VERSION = "bge-m3-int8"
EMBED_DIMENSIONS = 1024

# ONNX export of BAAI/bge-reranker-v2-m3 (Apache-2.0).
RERANK_REPO_ID = "onnx-community/bge-reranker-v2-m3-ONNX"
RERANK_REPO_FILES = (
    "onnx/model_int8.onnx",
    "tokenizer.json",
    "tokenizer_config.json",
    "special_tokens_map.json",
    "config.json",
)
RERANK_MODEL_FILE = "onnx/model_int8.onnx"
RERANK_SUBDIR = "bge-reranker-v2-m3"
# Part of the key of every cached score (model_version, at most 50 characters).
RERANK_MODEL_VERSION = "bge-reranker-v2-m3-int8"


def _flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class Settings:
    model_dir: Path
    internal_token: str
    allow_anonymous: bool
    auto_download: bool
    embed_max_tokens: int
    embed_max_batch: int
    embed_max_chars: int
    threads: int
    openai_api_key: str = ""
    llm_model: str = "gpt-6-luna"
    llm_timeout_seconds: float = 20.0
    llm_reasoning_effort: str = ""
    enrich_max_chars: int = 8000
    # /parse-query answers a person who is waiting: no reasoning pass and a short timeout.
    parse_reasoning_effort: str = "none"
    parse_timeout_seconds: float = 6.0
    rerank_enabled: bool = True
    # Per side: the query and each document are cut to this many tokens before being paired.
    rerank_max_tokens: int = 256
    rerank_max_documents: int = 40
    rerank_max_chars: int = 8000
    rerank_batch_size: int = 4

    @property
    def embed_dir(self) -> Path:
        return self.model_dir / EMBED_SUBDIR

    @property
    def rerank_dir(self) -> Path:
        return self.model_dir / RERANK_SUBDIR


def load_settings() -> Settings:
    default_model_dir = Path(__file__).resolve().parent.parent / "models"
    return Settings(
        model_dir=Path(os.getenv("AI_MODEL_DIR", str(default_model_dir))),
        internal_token=os.getenv("AI_INTERNAL_TOKEN", "").strip(),
        allow_anonymous=_flag("AI_ALLOW_ANONYMOUS", False),
        auto_download=_flag("AI_AUTO_DOWNLOAD", False),
        embed_max_tokens=int(os.getenv("AI_EMBED_MAX_TOKENS", "512")),
        embed_max_batch=int(os.getenv("AI_EMBED_MAX_BATCH", "32")),
        embed_max_chars=int(os.getenv("AI_EMBED_MAX_CHARS", "8000")),
        threads=int(os.getenv("AI_THREADS", "0")),
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        llm_model=os.getenv("AI_LLM_MODEL", "gpt-6-luna").strip() or "gpt-6-luna",
        llm_timeout_seconds=float(os.getenv("AI_LLM_TIMEOUT_SECONDS", "20")),
        llm_reasoning_effort=os.getenv("AI_LLM_REASONING_EFFORT", "").strip(),
        enrich_max_chars=int(os.getenv("AI_ENRICH_MAX_CHARS", "8000")),
        parse_reasoning_effort=os.getenv("AI_PARSE_REASONING_EFFORT", "none").strip(),
        parse_timeout_seconds=float(os.getenv("AI_PARSE_TIMEOUT_SECONDS", "6")),
        rerank_enabled=_flag("AI_RERANK_ENABLED", True),
        rerank_max_tokens=int(os.getenv("AI_RERANK_MAX_TOKENS", "256")),
        rerank_max_documents=int(os.getenv("AI_RERANK_MAX_DOCUMENTS", "40")),
        rerank_max_chars=int(os.getenv("AI_RERANK_MAX_CHARS", "8000")),
        rerank_batch_size=int(os.getenv("AI_RERANK_BATCH_SIZE", "4")),
    )
