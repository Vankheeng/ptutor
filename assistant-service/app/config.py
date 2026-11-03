"""Cấu hình của assistant-service, đọc từ biến môi trường.

Thiết kế 11 mục 11.2 (danh sách biến môi trường). Mọi tham số của harness (ngưỡng, ngân sách...) nằm ở đây
để đổi được mà không sửa code, và để `config_hash` (thiết kế 13 mục 13.3) băm được toàn bộ cấu hình.

Quy ước tên: biến của trợ lý có tiền tố `ASSISTANT_` (ví dụ `ASSISTANT_INTERNAL_TOKEN` → `internal_token`).
Vài biến dùng chung với `ai-service` giữ tên gốc (`OPENAI_API_KEY`, `AI_SERVICE_URL`, `AI_INTERNAL_TOKEN`).
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ASSISTANT_", env_file=".env", extra="ignore")

    # --- Kết nối (M1) ---
    # Chuỗi bí mật dùng chung với backend. Rỗng = chưa cấu hình → mọi /v1/** trả 503 (không mở cửa tự do).
    internal_token: str = ""
    database_url: str = ""
    backend_base_url: str = "http://127.0.0.1:8080"

    # --- Model ngôn ngữ (M2) ---
    llm_model: str = ""
    # Model viết lại câu hỏi và verifier mặc định dùng chung model Actor (thiết kế 11.2).
    rewrite_model: str = ""
    verifier_model: str = ""
    openai_api_key: str = Field(
        default="", validation_alias=AliasChoices("OPENAI_API_KEY", "ASSISTANT_OPENAI_API_KEY", "openai_api_key")
    )
    # Để trống = endpoint mặc định của OpenAI; đặt khi dùng nhà cung cấp tương thích OpenAI khác.
    openai_base_url: str = Field(
        default="", validation_alias=AliasChoices("OPENAI_BASE_URL", "ASSISTANT_OPENAI_BASE_URL", "openai_base_url")
    )
    llm_timeout_s: float = 15.0
    llm_temperature: float = 0.0
    llm_max_output_tokens: int = 1200

    @property
    def llm_enabled(self) -> bool:
        """LLM chỉ bật khi có cả khoá API và tên model; thiếu một trong hai thì /v1/chat trả 503."""
        return bool(self.openai_api_key and self.llm_model)

    @property
    def effective_rewrite_model(self) -> str:
        return self.rewrite_model or self.llm_model

    @property
    def effective_verifier_model(self) -> str:
        return self.verifier_model or self.llm_model


@lru_cache
def get_settings() -> Settings:
    """Đọc cấu hình một lần cho cả tiến trình. Test tạo `Settings(...)` riêng thay vì gọi hàm này."""
    return Settings()
