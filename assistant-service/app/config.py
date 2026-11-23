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

    # --- Kho tri thức (M3, M4) ---
    # File tokenizer.json của bge-m3; trống = models/bge-m3-tokenizer.json, không có file thì đếm ước lượng.
    tokenizer_path: str = ""
    # Danh sách route ban đầu (03 mục 3.8); sau khi import nằm ở kb_setting.routes.
    routes_file: str = "../knowledge-base/_routes.yaml"
    # Nơi tìm tài liệu khi trả lời: `pg` (bản thật) hoặc `memory` (đọc thẳng thư mục Markdown, chỉ để chạy thử /
    # eval khi chưa có Postgres — xem app/retrieval/store.py).
    knowledge_store: str = "pg"
    memory_kb_path: str = "../knowledge-base"

    # --- Embedding và rerank (M5, M6) ---
    # Mặc định `fake` và tắt rerank: ai-service chỉ có trên nhánh recommendation chưa merge (quyết định người dùng).
    # Thiết kế 11.2 ghi mặc định `ai-service` / rerank bật — đổi lại khi ai-service vào dev.
    embedding_provider: str = "fake"  # fake | ai-service
    ai_service_url: str = Field(default="http://127.0.0.1:8000",
                                validation_alias=AliasChoices("AI_SERVICE_URL", "ai_service_url"))
    ai_internal_token: str = Field(default="", validation_alias=AliasChoices("AI_INTERNAL_TOKEN", "ai_internal_token"))
    rerank_enabled: bool = False

    # --- Truy xuất (M6, thiết kế 04 mục 4.9) ---
    retrieval_mode: str = "hybrid"  # hybrid | vector (thí nghiệm E1)
    retrieval_k_each: int = 20
    rerank_k: int = 20
    context_k: int = 6
    rrf_k: int = 60
    # CẢNH BÁO (E1 chặng 8, bge-reranker-v2-m3): với 0,3 trên test ~62% câu trả lời được bị NO_CONTEXT → chưa bật
    # rerank khi chưa hiệu chỉnh lại ngưỡng trên dev (`eval.retrieval_eval` giờ lưu điểm thô để dò lại).
    min_rerank_score: float = 0.3
    min_cosine: float = 0.45
    min_text_coverage: float = 0.5

    # --- Harness và ngân sách mỗi lượt (M8, thiết kế 04 mục 4.9) ---
    max_tool_calls: int = 3
    max_model_calls: int = 4  # không tính verifier
    max_verify_retries: int = 1
    turn_token_budget: int = 40_000  # mọi lời gọi trong lượt, kể cả viết lại câu hỏi và verifier
    turn_timeout_s: float = 25.0
    context_token_cap: int = 10_000
    max_message_chars: int = 1000

    # --- Bộ nhớ hội thoại (M9, thiết kế 04 mục 4.10) ---
    history_turns: int = 6
    history_token_budget: int = 1500
    max_conversation_turns: int = 20  # quá mốc này widget chỉ gợi ý mở cuộc mới, không chặn
    rewrite_enabled: bool = True  # tắt để chạy thí nghiệm E4

    # --- Lớp chặn ngoài phạm vi (M12, thiết kế 04 mục 4.11) ---
    scope_guard_enabled: bool = True
    scope_tau_in: float = 0.55
    # Chặng 8 thử 0.02 (chọn trên dev): cổng hồi quy chặn vì chặn nhầm "Sửa số điện thoại ở đâu?" → giữ 0.05.
    scope_delta: float = 0.05

    # --- Kiểm chứng trích dẫn (M13, thiết kế 04 mục 4.6) ---
    verifier_enabled: bool = True  # tắt để chạy thí nghiệm E2 "không kiểm chứng"
    verifier_timeout_s: float = 8.0

    # --- Công cụ tra cứu (M14, thiết kế 05) ---
    tools_mode: str = "live"  # live | mock | off
    tool_catalog_path: str = "../tool-catalog/tools.yaml"
    tool_fixtures_dir: str = "eval/fixtures/scenarios"
    tool_scenario: str = "student_two_contracts"  # kịch bản mock mặc định (demo không cần backend)
    tool_timeout_s: float = 5.0
    max_tools_exposed: int = 6
    # Múi giờ JVM backend cho trường thời điểm BACKEND_JVM (quyết định 2026-11-03: backend giữ giờ Việt Nam).
    backend_jvm_time_zone: str = "Asia/Ho_Chi_Minh"

    # `openai` (mặc định) hoặc `scripted`: Actor trích xuất tất định của eval (eval/scripted_policy.py) — CHỈ để demo /
    # chụp màn hình khi chưa có model; câu trả lời là câu chép từ tài liệu, không phải model ngôn ngữ.
    llm_provider: str = "openai"

    @property
    def llm_enabled(self) -> bool:
        """LLM chỉ bật khi có cả khoá API và tên model; thiếu một trong hai thì /v1/chat trả 503."""
        return self.llm_provider == "scripted" or bool(self.openai_api_key and self.llm_model)

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
