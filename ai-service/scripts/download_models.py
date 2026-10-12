"""Download the model files into ai-service/models (or AI_MODEL_DIR).

    python scripts/download_models.py --dry-run   # list files and sizes, download nothing
    python scripts/download_models.py             # download what is missing
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import EMBED_REPO_ID, load_settings  # noqa: E402
from app.model_store import download_embed_files, missing_embed_files, remote_embed_file_sizes  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="only print what would be downloaded")
    args = parser.parse_args()

    settings = load_settings()
    missing = set(missing_embed_files(settings.embed_dir))
    sizes = remote_embed_file_sizes()

    print(f"Source : https://huggingface.co/{EMBED_REPO_ID}")
    print(f"Target : {settings.embed_dir}")
    for name, size in sorted(sizes.items(), key=lambda item: -item[1]):
        print(f"  {size / 1e6:9.1f} MB  {name}  {'(missing)' if name in missing else '(already present)'}")
    to_download = sum(size for name, size in sizes.items() if name in missing)
    print(f"To download: {to_download / 1e6:.1f} MB of {sum(sizes.values()) / 1e6:.1f} MB")

    if args.dry_run or not missing:
        return 0
    download_embed_files(settings.embed_dir)
    left = missing_embed_files(settings.embed_dir)
    if left:
        print("Still missing: " + ", ".join(left), file=sys.stderr)
        return 1
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
