"""`CitationVerifierMiddleware` — hook ④' (câu trả lời cuối) — thiết kế 04 mục 4.6, QĐ11.

Ba loại lỗi trích dẫn và lớp bắt:
1. Khẳng định không có nguồn (intent POLICY / HOWTO)     → code.
2. Nguồn bịa (`D9` khi chỉ có D1–D6, tool chưa gọi)       → code.
3. Gán sai nguồn (D1 có thật nhưng không nói điều này)    → **verifier** (lời gọi LLM riêng)
   + kiểm bằng chứng bằng code.

Verifier chỉ thấy danh sách khẳng định + nguyên văn đoạn nguồn — **không** thấy câu hỏi, lịch sử hay câu trả lời
hoàn chỉnh của Actor (tránh bị Actor "thuyết phục"). Nhiệt độ 0, có thể dùng model khác (`ASSISTANT_VERIFIER_MODEL`).
Khẳng định lấy từ tool: kiểm bằng code — số tiền, ngày, mã trạng thái phải có trong kết quả tool của lượt.

Xử lý (giới hạn cứng):
    mọi khẳng định đạt               → chấp nhận, grounded = FULL
    chưa đạt, chưa thử lại           → RETRY kèm góp ý "khẳng định #i sai vì …" (tối đa 1 lần)
    chưa đạt, đã thử lại             → giữ khẳng định đạt, viết lại câu trả lời từ chúng, grounded = PARTIAL
    không còn khẳng định nào đạt     → ABSTAIN (VERIFY_FAILED)

Chỉ chạy cho intent POLICY, HOWTO, LOOKUP; CHITCHAT / OUT_OF_SCOPE không cần.
"""

from __future__ import annotations

import json

from app.agent.middleware.base import Middleware
from app.agent.middleware.budget import charge_side_call
from app.agent.output_schema import Claim
from app.agent.prompts import load_prompt
from app.agent.state import Decision, TurnState
from app.agent.trace import timed
from app.agent.verification import VERIFIER_SCHEMA, Verdict, claim_matches_tool_data, normalize_verdict
from app.providers.llm import ChatMessage, LlmError, LlmProvider, LlmRequest, LlmResponse

CHECKED_INTENTS = {"POLICY", "HOWTO", "LOOKUP"}
NEEDS_SOURCE = {"POLICY", "HOWTO"}


class CitationVerifierMiddleware(Middleware):
    name = "citation_verifier"

    def __init__(self, llm: LlmProvider, model: str, *, max_retries: int = 1, timeout_s: float = 8.0,
                 use_llm_verifier: bool = True):
        self._llm = llm
        self._model = model
        self._max_retries = max_retries
        self._timeout_s = timeout_s
        self._use_llm = use_llm_verifier  # tắt để chạy thí nghiệm E2 "không kiểm chứng"

    def after_model(self, state: TurnState, response: LlmResponse) -> Decision:
        output = state.output
        if response.tool_calls or output is None or output.intent not in CHECKED_INTENTS:
            return Decision.accept()

        verdicts = self._check(state, output.claims, output.intent)
        failed = [verdict for verdict in verdicts if verdict.verdict != "SUPPORTED"]
        state.verification = {
            "verdicts": [verdict.__dict__ for verdict in verdicts],
            "retries": state.verify_retries,
            "grounded": "FULL" if verdicts and not failed else None,
        }
        if not failed:
            if not verdicts and output.intent in NEEDS_SOURCE:
                # Không có khẳng định nào: câu trả lời kiểu "chưa có thông tin" → không có gì để kiểm, nhưng cũng không
                # phải câu trả lời có căn cứ.
                state.verification["grounded"] = "NONE"
            return Decision.accept()
        if state.verify_retries < self._max_retries:
            state.verify_retries += 1
            return Decision.retry(self._feedback(output.claims, failed), reason="VERIFY_FAILED")

        supported = [output.claims[verdict.claim] for verdict in verdicts if verdict.verdict == "SUPPORTED"]
        if not supported:
            state.verification["reason"] = "VERIFY_FAILED"
            return Decision.abstain("VERIFY_FAILED")
        # Bỏ khẳng định sai: dựng lại câu trả lời chỉ từ khẳng định đạt (không giữ câu sai trong `answer`).
        output.claims = supported
        output.answer = "\n".join(f"- {claim.text} [{claim.source}]" if claim.source.startswith("D")
                                  else f"- {claim.text}" for claim in supported)
        state.verification.update({"grounded": "PARTIAL", "reason": "VERIFY_PARTIAL"})
        return Decision.accept()

    # ------------------------------------------------------------------ kiểm từng khẳng định

    def _check(self, state: TurnState, claims: list[Claim], intent: str) -> list[Verdict]:
        sections = {section.label: section for section in (state.retrieval.sections if state.retrieval else [])}
        tools_called = {f"tool:{record.call.name}" for record in state.tool_calls if record.blocked_reason is None}
        tool_texts = [record.result_text for record in state.tool_calls if record.blocked_reason is None]
        verdicts: dict[int, Verdict] = {}
        to_verify: list[int] = []
        for index, claim in enumerate(claims):
            source = claim.source.strip()
            if not source:
                if intent in NEEDS_SOURCE:
                    verdicts[index] = Verdict(index, "UNSUPPORTED", reason="Khẳng định không ghi nguồn")
                else:
                    verdicts[index] = Verdict(index, "SUPPORTED", reason="Không cần nguồn")
            elif source.startswith("tool:"):
                if source not in tools_called:
                    verdicts[index] = Verdict(index, "UNSUPPORTED", reason=f"Chưa gọi {source} trong lượt này")
                else:
                    ok, missing = claim_matches_tool_data(claim.text, tool_texts)
                    verdicts[index] = Verdict(index, "SUPPORTED" if ok else "UNSUPPORTED",
                                              reason="" if ok else f"Không có trong kết quả tra cứu: {sorted(missing)}")
            elif source not in sections:
                verdicts[index] = Verdict(index, "UNSUPPORTED", reason=f"Nguồn {source} không tồn tại")
            else:
                to_verify.append(index)
        if to_verify:
            for verdict in self._verify_with_llm(state, claims, to_verify, sections):
                verdicts[verdict.claim] = verdict
        return [verdicts[index] for index in sorted(verdicts)]

    def _verify_with_llm(self, state: TurnState, claims: list[Claim], indexes: list[int], sections) -> list[Verdict]:
        if not self._use_llm:
            return [Verdict(index, "SUPPORTED", reason="Không chạy verifier (cấu hình E2)") for index in indexes]
        lines = []
        for index in indexes:
            section = sections[claims[index].source]
            lines.append(f'<claim id="{index}" source="{section.label}">{claims[index].text}</claim>')
        sources = "\n".join(f'<source id="{label}">\n{sections[label].content}\n</source>'
                            for label in sorted({claims[index].source for index in indexes}))
        request = LlmRequest(
            messages=[ChatMessage("system", load_prompt("verifier")),
                      ChatMessage("user", sources + "\n\n" + "\n".join(lines))],
            model=self._model, response_schema=VERIFIER_SCHEMA, response_schema_name="verdicts",
            temperature=0.0, max_output_tokens=800, timeout_s=self._timeout_s, purpose="verify",
        )
        name = "retry_verify" if state.verify_retries else "verify"
        with timed(state, name, "verify") as span:
            try:
                response = self._llm.complete(request)
                raw = json.loads(response.content)["verdicts"]
            except (LlmError, ValueError, KeyError, TypeError):
                # Không kiểm chứng được → không cho khẳng định về quy định đi qua mà chưa kiểm (ưu tiên không bịa).
                span.status = "ERROR"
                return [Verdict(index, "UNSUPPORTED", reason="Không kiểm chứng được") for index in indexes]
            span.tokens_in, span.tokens_out = response.tokens_in, response.tokens_out
            charge_side_call(state, response)
        by_claim = {}
        for item in raw:
            if isinstance(item, dict) and item.get("claim") in indexes:
                verdict = Verdict(int(item["claim"]), str(item.get("verdict", "UNSUPPORTED")),
                                  str(item.get("evidence", "")), str(item.get("reason", "")))
                by_claim[verdict.claim] = normalize_verdict(verdict, sections[claims[verdict.claim].source].content)
        # Verifier bỏ sót khẳng định nào → coi như chưa được chứng minh.
        return [by_claim.get(index, Verdict(index, "UNSUPPORTED", reason="Verifier không chấm")) for index in indexes]

    @staticmethod
    def _feedback(claims: list[Claim], failed: list[Verdict]) -> str:
        items = []
        for verdict in failed:
            claim = claims[verdict.claim]
            items.append(f'Khẳng định #{verdict.claim} "{claim.text}" (nguồn {claim.source or "trống"}) '
                         f"không đạt: {verdict.reason or verdict.verdict}.")
        return " ".join(items) + (" Bỏ các khẳng định này hoặc dùng đúng đoạn tài liệu có nội dung đó; "
                                  "không thêm thông tin.")
