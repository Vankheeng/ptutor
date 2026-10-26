"""Shared helpers of the evaluation scripts: paths, ids, database access and the HTTP client."""

import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path
from urllib import error, request


def _scenario() -> str:
    """--scenario NAME anywhere on the command line (removed from sys.argv), else EVAL_SCENARIO, else base."""
    if "--scenario" in sys.argv:
        index = sys.argv.index("--scenario")
        name = sys.argv[index + 1]
        del sys.argv[index:index + 2]
        return name
    return os.environ.get("EVAL_SCENARIO", "base")


SCENARIO = _scenario()
ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out" / SCENARIO
REPO = ROOT.parents[1]
RESULTS = REPO / "docs" / "recommendation" / "ket-qua" / SCENARIO

SEED = 20261020
NAMESPACE = uuid.UUID("3f1c9a52-8d47-4b6e-a0c1-5e2f7d9b4a63")
EMAIL_DOMAIN = "ptutor.local"
# Same bcrypt hash as the demo data (scripts/recommendation_demo): local use only.
PASSWORD = "Demo@2026"
PASSWORD_HASH = "$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG"

CONTAINER = "ptutor-postgres"
# Every scenario runs in its own database, rebuilt by run.py setup; the dev database is never touched.
DATABASE = os.environ.get("EVAL_DB", "ptutor_eval")
DB_URL = f"jdbc:postgresql://localhost:5433/{DATABASE}"
API = "http://localhost:8080"


def free_ram_gb() -> float | None:
    """Free physical memory, noted next to every latency (16 GB machine; design 13, 13.2). Windows only."""
    if os.name != "nt":
        return None
    import ctypes

    class MemoryStatus(ctypes.Structure):
        _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong), ("total", ctypes.c_ulonglong),
                    ("available", ctypes.c_ulonglong), ("total_page", ctypes.c_ulonglong),
                    ("available_page", ctypes.c_ulonglong), ("total_virtual", ctypes.c_ulonglong),
                    ("available_virtual", ctypes.c_ulonglong), ("available_extended", ctypes.c_ulonglong)]

    status = MemoryStatus()
    status.length = ctypes.sizeof(MemoryStatus)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
    return round(status.available / 2 ** 30, 2)


def uid(*parts) -> str:
    return str(uuid.uuid5(NAMESPACE, "/".join(str(part) for part in parts)))


def q(value) -> str:
    """SQL literal."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def psql(sql: str, database: str = DATABASE) -> str:
    """Runs SQL in the dev database container and returns stdout (unaligned, tuples only)."""
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-v", "ON_ERROR_STOP=1", "-q", "-At",
         "-U", "postgres", "-d", database],
        input=sql.encode("utf-8"), capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8")


def query_json(sql: str):
    """Rows of a SELECT as a list of dicts (the query is wrapped in json_agg)."""
    text = psql(f"SELECT COALESCE(json_agg(t), '[]'::json) FROM ({sql}) t;").strip()
    return json.loads(text or "[]")


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


class Api:
    """Minimal JSON client of the backend; one instance per logged-in user."""

    def __init__(self, base: str = API):
        self.base = base
        self.token = None
        self.credentials = None

    def call(self, method: str, path: str, body=None, retries: int = 3):
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Content-Type": "application/json", "Origin": "http://localhost:5173"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        for attempt in range(retries):
            try:
                req = request.Request(self.base + path, data=data, method=method, headers=headers)
                with request.urlopen(req, timeout=120) as response:
                    payload = response.read()
                    return json.loads(payload) if payload else None
            except error.HTTPError as failure:
                detail = failure.read().decode("utf-8", errors="replace")
                if failure.code == 401 and self.credentials and attempt + 1 < retries:
                    self.login(*self.credentials)  # the access token expired during a long run
                    headers["Authorization"] = "Bearer " + self.token
                    continue
                if failure.code >= 500 and attempt + 1 < retries:
                    time.sleep(2)
                    continue
                raise RuntimeError(f"{method} {path} -> {failure.code}: {detail[:400]}") from None
            except error.URLError:
                if attempt + 1 < retries:
                    time.sleep(3)
                    continue
                raise
        return None

    def login(self, email: str, password: str = PASSWORD) -> "Api":
        self.token = None
        self.credentials = None  # no retry-by-login while logging in
        body = self.call("POST", "/api/v1/auth/login", {"email": email, "password": password})
        self.token = body["data"]["accessToken"]
        self.credentials = (email, password)
        return self


def wait_for_api(timeout: float = 300) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with request.urlopen(API + "/actuator/health", timeout=5):
                return
        except error.HTTPError:
            return  # the server answers, even if health is protected
        except Exception:  # noqa: BLE001 - not up yet
            time.sleep(2)
    raise TimeoutError("backend did not start")
