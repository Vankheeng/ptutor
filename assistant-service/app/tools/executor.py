"""Thực thi một lời gọi tool **đã qua ToolGate**: dựng đường dẫn + tham số, gọi backend, lọc kết quả
(thiết kế 05 mục 5.4, 5.5). Đây là lớp trong cùng của chuỗi `wrap_tool_call`.

- Tham số cố định (`fixed_params`, ví dụ `source=PAYMENT`, `size=100`) do code gắn, đè mọi giá trị khác.
- Token: `Authorization` của người hỏi, do harness gắn — model không thấy, không sửa được.
- Lỗi HTTP → câu báo ngắn cho model (05 mục 5.5); không bao giờ đưa nguyên văn lỗi tiếng Anh của máy chủ.
"""

from __future__ import annotations

import json
import time
from datetime import datetime
from zoneinfo import ZoneInfo

from app.agent.state import ToolCallRecord, TurnState
from app.providers.llm import LlmToolCall
from app.tools.backend_client import BackendClient
from app.tools.registry import ToolRegistry, fill_path
from app.tools.result_filter import VIETNAM, FilterContext, shape_result

MESSAGES = {
    401: "Phiên đăng nhập đã hết hạn nên chưa tra cứu được. Người dùng cần đăng nhập lại rồi hỏi lại.",
    403: "Bạn không có quyền xem thông tin này. Không thử công cụ khác để vòng qua.",
    404: "Không tìm thấy thông tin này trong tài khoản của bạn. Không suy đoán.",
    "unavailable": "Tạm thời chưa tra cứu được dữ liệu này. Vẫn trả lời phần dựa trên tài liệu nếu có, "
                   "không bịa dữ liệu.",
}


class ToolExecutor:
    def __init__(self, registry: ToolRegistry, client: BackendClient, backend_zone: str = "Asia/Ho_Chi_Minh"):
        self._registry = registry
        self._client = client
        self._backend_zone = ZoneInfo(backend_zone)

    def __call__(self, state: TurnState, call: LlmToolCall) -> ToolCallRecord:
        tool = self._registry.get(call.name)
        assert tool is not None, "ToolGate phải chặn tool lạ trước khi tới đây"
        path_values = {param.name: str(call.arguments[param.name]) for param in tool.params
                       if param.location == "path"}
        query = {param.name: self._query_value(param.type, call.arguments[param.name]) for param in tool.params
                 if param.location == "query" and call.arguments.get(param.name) is not None}
        query.update(tool.fixed_params)
        started = time.perf_counter()
        response = self._client.get(tool.name, fill_path(tool.path, path_values), query,
                                    state.caller.authorization, path_values)
        record = ToolCallRecord(call=call, http_status=response.status or None,
                                latency_ms=int((time.perf_counter() - started) * 1000))
        if response.status != 200:
            record.result_text = self._error_text(tool.name, response.status, response.code)
            return record
        filtered = shape_result(tool, response.data, FilterContext(str(state.caller.user_id), self._backend_zone))
        record.result_data = filtered.data
        record.result_items = filtered.items_count
        record.focus_items = filtered.focus_items
        body = json.dumps(filtered.data, ensure_ascii=False, separators=(",", ":"))
        # Bọc thẻ: nội dung bên trong là DỮ LIỆU, kể cả khi người khác viết câu "lệnh" vào đó (05 mục 5.1 ý 7).
        record.result_text = f'<tool_result tool="{tool.name}">{body}</tool_result>'
        return record

    def _query_value(self, java_type: str, value):
        """Tham số thời gian model điền theo giờ Việt Nam → đổi sang múi giờ backend trước khi gọi."""
        if java_type == "LocalDateTime" and isinstance(value, str):
            try:
                moment = datetime.fromisoformat(value)
            except ValueError:
                return value
            local = moment.replace(tzinfo=VIETNAM) if moment.tzinfo is None else moment
            return local.astimezone(self._backend_zone).replace(tzinfo=None).isoformat(timespec="seconds")
        return value

    def _error_text(self, tool_name: str, status: int, code: str) -> str:
        tool = self._registry.get(tool_name)
        known = (tool.errors.get(code) if tool else None) or {}
        if known.get("user_message"):
            message = known["user_message"]
        elif status in MESSAGES:
            message = MESSAGES[status]
        elif status in {409, 400, 422} and known.get("meaning"):
            message = f"Không tra cứu được: {known['meaning']}."
        else:
            message = MESSAGES["unavailable"]
        return f'<tool_result tool="{tool_name}" error="{code or status}">{message}</tool_result>'
