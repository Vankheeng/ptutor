"""Bản "Actor trích xuất" tất định cho eval tầng T0–T2 (không cần model, không tốn tiền).

Đây **không** phải mô hình ngôn ngữ: nó làm đúng những việc tối thiểu để cả đường ống chạy được và các grader có dữ
liệu thật để chấm — truy xuất, chặn ngoài phạm vi, ToolGate, LoopGuard, lọc trường, kiểm chứng, lưu trace:

- Viết lại câu hỏi: nếu có đối tượng đang bàn và câu hỏi dùng đại từ ("nó", "cái đó", "còn cái…") → ghép nhãn đối
  tượng nhắc gần nhất vào câu.
- Actor: câu hỏi về dữ liệu của chính người hỏi (cùng dấu hiệu với ToolSelection) hoặc dùng đại từ chỉ đối tượng
  đang bàn, có tool và chưa gọi gì → gọi tool hợp nhất (tool chi tiết dùng ID trong sổ tay phiên); có kết quả tool →
  trả LOOKUP trích số liệu từ mục đầu; có tài liệu → trích **câu có nhiều từ chung với câu hỏi nhất** của D1 làm
  khẳng định, nhưng nếu câu đó phủ dưới `COVERAGE_MIN` số từ của câu hỏi thì coi như tài liệu không trả lời → "chưa có
  thông tin" (grounded NONE); không có gì → "chưa có thông tin". Câu hỏi "làm sao / ở đâu / cách…" → intent HOWTO.
- Phiên bản 2 (chặng 8): thêm điều kiện gọi tool, intent HOWTO, ngưỡng phủ. `COVERAGE_MIN` chọn trên tập **dev**.
- Verifier: SUPPORTED khi khẳng định là chuỗi con của đoạn nguồn (vì Actor trích nguyên văn), còn lại UNSUPPORTED.

Kết quả eval với policy này đo **harness và truy xuất**, không đo chất lượng diễn đạt của LLM. Báo cáo phải ghi rõ.
"""

from __future__ import annotations

import json
import re

from app.agent.middleware.tool_selection import is_personal_question
from app.agent.verification import normalize_for_evidence
from app.providers.llm import LlmRequest, LlmResponse, LlmToolCall
from app.retrieval.store import query_terms
from app.text import strip_accents

_DOC = re.compile(r'<doc id="(D\d+)"[^>]*>\n(.*?)\n</doc>', re.S)
_QUESTION = re.compile(r"<question>(.*?)</question>", re.S)
_FOCUS = re.compile(r"- (\w+) ([0-9a-f-]{36}): (.*)")
_PRONOUN = re.compile(r"\b(nó|cái đó|cái kia|còn cái|hợp đồng kia|hợp đồng đó|cái tiếng|cái toán)\b", re.I)
_SOURCE = re.compile(r'<source id="(D\d+)">\n(.*?)\n</source>', re.S)
_UUID = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b")
_CLAIM = re.compile(r'<claim id="(\d+)" source="(D\d+)">(.*?)</claim>', re.S)
_HOWTO = re.compile(r"\b(lam sao|lam the nao|the nao|cach|o dau|bam|thao tac|cac buoc|can nhung gi|can gi)\b")
# Tỉ lệ từ của câu hỏi phải có trong câu trích để coi là tài liệu trả lời được (chọn trên dev, xem BAO-CAO-DANH-GIA).
COVERAGE_MIN = 0.0


def scripted_policy(request: LlmRequest) -> LlmResponse:
    if request.purpose == "rewrite":
        return _rewrite(request)
    if request.purpose == "verify":
        return _verify(request)
    return _actor(request)


def _tokens(text: str) -> int:
    return max(1, len(text) // 4)


def _respond(content: str = "", tool_calls: list[LlmToolCall] | None = None, request: LlmRequest | None = None):
    prompt = sum(len(message.content) for message in request.messages) if request else 0
    return LlmResponse(content=content, tool_calls=tool_calls or [], tokens_in=prompt // 4,
                       tokens_out=_tokens(content) + 20 * len(tool_calls or []), model="scripted-extractive")


def _question_in(text: str) -> str:
    match = _QUESTION.search(text)
    return match.group(1).strip() if match else ""


def _rewrite(request: LlmRequest) -> LlmResponse:
    user = request.messages[-1].content
    question = _question_in(user)
    focus = _FOCUS.findall(user)
    if focus and _PRONOUN.search(question):
        kind, _, label = focus[-1]
        question = f"{question} ({kind} {label})"
    return _respond(question, request=request)


def _verify(request: LlmRequest) -> LlmResponse:
    content = request.messages[-1].content
    sources = dict(_SOURCE.findall(content))
    verdicts = []
    for claim_id, source, text in _CLAIM.findall(content):
        supported = normalize_for_evidence(text) in normalize_for_evidence(sources.get(source, ""))
        verdicts.append({"claim": int(claim_id), "verdict": "SUPPORTED" if supported else "UNSUPPORTED",
                         "evidence": text if supported else "", "reason": "trích nguyên văn" if supported else ""})
    return _respond(json.dumps({"verdicts": verdicts}, ensure_ascii=False), request=request)


def _actor(request: LlmRequest) -> LlmResponse:
    user_messages = [message for message in request.messages if message.role == "user"]
    context = next((m.content for m in reversed(user_messages) if "<question>" in m.content), "")
    question = _question_in(context)
    tool_results = [message.content for message in request.messages if message.role == "tool"]
    wants_data = (is_personal_question(question) or bool(_PRONOUN.search(question) and _FOCUS.search(context))
                  or _names_focus_item(question, context))
    if request.tools and not tool_results and wants_data:
        call = _pick_tool(request, question, context)
        if call is not None:
            return _respond(tool_calls=[call], request=request)
    if tool_results and any("error=" not in text and "blocked=" not in text for text in tool_results):
        return _respond(_lookup_answer(tool_results), request=request)
    intent = "HOWTO" if _HOWTO.search(" " + strip_accents(question.lower()) + " ") else "POLICY"
    documents = _DOC.findall(context)
    if documents:
        label, content = documents[0]
        sentence = _best_sentence(content, question)
        if sentence and _coverage(question, sentence) >= COVERAGE_MIN:
            return _respond(_answer(intent, [{"text": sentence, "source": label}], f"{sentence} [{label}]"),
                            request=request)
    return _respond(_answer(intent, [], "Mình chưa có thông tin chính thức về vấn đề này."), request=request)


def _names_focus_item(question: str, context: str) -> bool:
    """Câu hỏi gọi tên một đối tượng đang bàn ("hợp đồng Toán có buổi nào…") — model thật sẽ tra dữ liệu của nó."""
    asked = " " + strip_accents(question.lower()) + " "
    names = [strip_accents(label.split("·")[0].strip().lower()) for _, _, label in _FOCUS.findall(context)]
    return any(len(name) >= 3 and f" {name} " in asked for name in names)


def _coverage(question: str, sentence: str) -> float:
    terms = set(query_terms(question))
    return len(terms & set(query_terms(sentence))) / len(terms) if terms else 0.0


def _pick_tool(request: LlmRequest, question: str, context: str) -> LlmToolCall | None:
    focus = _FOCUS.findall(context)
    # Mô phỏng model "nghe lời" người dùng: mã UUID có trong câu hỏi được dùng thẳng làm tham số — để bộ an toàn
    # kiểm được ToolGate thật sự chặn mã chưa từng xuất hiện (13 mục 13.7 "Đoán ID").
    typed_ids = _UUID.findall(question)
    if typed_ids:
        focus = [("contract", typed_ids[0], "")] + focus
    terms = set(query_terms(question))
    best, best_score = None, -1
    for tool in request.tools:
        required = tool.parameters.get("required", [])
        score = len(terms & set(query_terms(tool.description)))
        if required and not focus:
            score -= 5  # cần ID mà chưa có → không chọn trước tool liệt kê
        if score > best_score:
            best, best_score = tool, score
    if best is None:
        return None
    arguments = {}
    for name in best.parameters.get("required", []):
        if not focus:
            return None
        arguments[name] = _focus_for(question, focus)
    return LlmToolCall(id=f"call-{best.name}", name=best.name, arguments=arguments)


def _focus_for(question: str, focus: list[tuple[str, str, str]]) -> str:
    if _UUID.search(question):
        return focus[0][1]
    terms = set(query_terms(question))
    ranked = sorted(focus, key=lambda item: len(terms & set(query_terms(item[2]))), reverse=True)
    return ranked[0][1]


def _lookup_answer(tool_results: list[str]) -> str:
    text = next(t for t in reversed(tool_results) if "error=" not in t and "blocked=" not in t)
    name_match = re.search(r'tool="(\w+)"', text)
    body_match = re.search(r">(.*)</tool_result>", text, re.S)
    if name_match is None or body_match is None:
        return _answer("LOOKUP", [], "Mình đã tra cứu nhưng chưa đọc được dữ liệu.")
    name = name_match.group(1)
    body = json.loads(body_match.group(1))
    item = (body.get("items") or [body])[0] if isinstance(body, dict) else {}
    facts = [f"{value}" for key, value in item.items() if isinstance(value, str) and key.endswith(
        ("_label", "Name", "amount", "price", "dueDate", "balance", "Date"))][:4]
    sentence = ", ".join(facts) + "." if facts else "Mình đã tra cứu dữ liệu của bạn."
    if isinstance(body, dict) and "total" in body:
        sentence = f"Có {body['total']} mục. Mục đầu: {sentence}"
    return _answer("LOOKUP", [{"text": sentence, "source": f"tool:{name}"}], sentence)


def _best_sentence(content: str, question: str) -> str:
    terms = set(query_terms(question))
    sentences = [part.strip(" -*\n") for part in re.split(r"(?<=[.!?])\s+|\n+", content) if len(part.strip()) > 15]
    if not sentences:
        return ""
    return max(sentences, key=lambda sentence: len(terms & set(query_terms(sentence))))


def _answer(intent: str, claims: list[dict], answer: str) -> str:
    return json.dumps({"intent": intent, "claims": claims, "answer": answer, "actions": [], "suggestions": []},
                      ensure_ascii=False)
