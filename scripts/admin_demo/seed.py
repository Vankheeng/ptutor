"""Load repeatable local admin fixtures into the recommendation development database."""

import argparse
import base64
import hashlib
import hmac
import os
import re
import secrets
import subprocess
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def citizen_key() -> bytes:
    configured = os.environ.get("CITIZEN_ID_ENCRYPTION_KEY")
    if configured is None:
        config = Path(__file__).resolve().parents[2] / "backend/src/main/resources/application.yml"
        match = re.search(r"^\s*citizen-id-encryption-key:\s*(\S+)", config.read_text(encoding="utf-8"), re.M)
        if match is None:
            raise RuntimeError("Set CITIZEN_ID_ENCRYPTION_KEY to the backend's local key")
        configured = match.group(1)
    key = base64.b64decode(configured)
    if len(key) != 32:
        raise ValueError("Citizen ID encryption key must decode to 32 bytes")
    return key


def citizen_values(citizen_id: str, master: bytes) -> tuple[str, str]:
    encryption_key = hashlib.sha256(b"encryption\0" + master).digest()
    hash_key = hashlib.sha256(b"blind-index\0" + master).digest()
    iv = secrets.token_bytes(12)
    payload = iv + AESGCM(encryption_key).encrypt(iv, citizen_id.encode("utf-8"), None)
    encrypted = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
    blind_index = hmac.new(hash_key, citizen_id.encode("utf-8"), hashlib.sha256).hexdigest()
    return encrypted, blind_index


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", default="ptutor_recommendation_dev")
    parser.add_argument("--container", default="ptutor-postgres")
    args = parser.parse_args()
    sql = Path(__file__).with_name("seed.sql").read_text(encoding="utf-8")
    master = citizen_key()
    for label, citizen_id in (("ADMIN", "000000000000"), ("CS", "099900000001"),
                              ("ACCOUNTANT", "099900000002")):
        encrypted, blind_index = citizen_values(citizen_id, master)
        sql = sql.replace(f"__{label}_CITIZEN_ENCRYPTED__", encrypted)
        sql = sql.replace(f"__{label}_CITIZEN_HASH__", blind_index)
    result = subprocess.run(
        ["docker", "exec", "-i", args.container, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-q", "-U", "postgres", "-d", args.database],
        input=sql.encode("utf-8"),
        capture_output=True,
    )
    if result.returncode:
        print(result.stderr.decode("utf-8", errors="replace"))
        return result.returncode
    print(f"Admin demo data loaded into {args.database}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
