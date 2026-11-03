"""Băm cấu hình harness thành 12 ký tự (`turn_trace.config_hash`, thiết kế 13 mục 13.3).

Hai lần chạy cùng `config_hash` = cùng tham số, cùng phiên bản prompt, cùng model → so sánh được với nhau. Không
đưa khoá API, token, địa chỉ DB vào băm (không liên quan tới hành vi, và không được lộ).
"""

from __future__ import annotations

import hashlib
import json

from app.agent.prompts import PROMPT_VERSIONS
from app.config import Settings

SECRET_OR_ENVIRONMENT_FIELDS = {
    "internal_token", "database_url", "backend_base_url", "openai_api_key", "openai_base_url", "ai_service_url",
    "ai_internal_token", "tokenizer_path", "routes_file", "memory_kb_path", "tool_catalog_path",
}


def harness_config(settings: Settings) -> dict:
    values = {key: value for key, value in settings.model_dump().items() if key not in SECRET_OR_ENVIRONMENT_FIELDS}
    values["prompt_versions"] = PROMPT_VERSIONS
    return values


def config_hash(settings: Settings) -> str:
    payload = json.dumps(harness_config(settings), sort_keys=True, default=str, ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:12]
