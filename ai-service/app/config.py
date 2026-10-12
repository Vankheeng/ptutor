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

    @property
    def embed_dir(self) -> Path:
        return self.model_dir / EMBED_SUBDIR


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
    )
