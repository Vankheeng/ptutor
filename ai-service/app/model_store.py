"""Locating and downloading model files. Models live in a volume and are never committed.

The repository is public, so requests are sent without a token (`token=False`): a stale token
cached on the machine would otherwise make Hugging Face answer 401.
"""

from pathlib import Path

from .config import EMBED_REPO_FILES, EMBED_REPO_ID


def missing_embed_files(embed_dir: Path) -> list[str]:
    return [name for name in EMBED_REPO_FILES if not (embed_dir / name).is_file()]


def remote_embed_file_sizes() -> dict[str, int]:
    """Size in bytes of every file that would be downloaded, without downloading anything."""
    from huggingface_hub import HfApi

    wanted = set(EMBED_REPO_FILES)
    info = HfApi(token=False).model_info(EMBED_REPO_ID, files_metadata=True)
    return {s.rfilename: s.size or 0 for s in info.siblings if s.rfilename in wanted}


def download_embed_files(embed_dir: Path) -> None:
    from huggingface_hub import hf_hub_download

    embed_dir.mkdir(parents=True, exist_ok=True)
    for name in EMBED_REPO_FILES:
        hf_hub_download(repo_id=EMBED_REPO_ID, filename=name, local_dir=str(embed_dir), token=False)
