"""Download the model files into ai-service/models (or AI_MODEL_DIR).

    python scripts/download_models.py --dry-run   # list files and sizes, download nothing
    python scripts/download_models.py             # download what is missing
    python scripts/download_models.py --only embed
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import EMBED_REPO_ID, RERANK_REPO_ID, load_settings  # noqa: E402
from app.model_store import (  # noqa: E402
    download_embed_files,
    download_rerank_files,
    missing_embed_files,
    missing_rerank_files,
    remote_embed_file_sizes,
    remote_rerank_file_sizes,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="only print what would be downloaded")
    parser.add_argument("--only", choices=["embed", "rerank"], help="download one model instead of both")
    args = parser.parse_args()

    settings = load_settings()
    models = {
        "embed": (EMBED_REPO_ID, settings.embed_dir, missing_embed_files, remote_embed_file_sizes,
                  download_embed_files),
        "rerank": (RERANK_REPO_ID, settings.rerank_dir, missing_rerank_files, remote_rerank_file_sizes,
                   download_rerank_files),
    }
    failed = False
    for name, (repo_id, target, find_missing, remote_sizes, download) in models.items():
        if args.only and args.only != name:
            continue
        missing = set(find_missing(target))
        sizes = remote_sizes()
        print(f"Source : https://huggingface.co/{repo_id}")
        print(f"Target : {target}")
        for file_name, size in sorted(sizes.items(), key=lambda item: -item[1]):
            print(f"  {size / 1e6:9.1f} MB  {file_name}  "
                  f"{'(missing)' if file_name in missing else '(already present)'}")
        to_download = sum(size for file_name, size in sizes.items() if file_name in missing)
        print(f"To download: {to_download / 1e6:.1f} MB of {sum(sizes.values()) / 1e6:.1f} MB")

        if args.dry_run or not missing:
            continue
        download(target)
        left = find_missing(target)
        if left:
            print("Still missing: " + ", ".join(left), file=sys.stderr)
            failed = True
        else:
            print("Done.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
