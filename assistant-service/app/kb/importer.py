"""Nạp bản khởi tạo từ thư mục `knowledge-base/` vào DB dưới dạng nháp (thiết kế 03 mục 3.6).

    .venv/Scripts/python -m app.kb.import --path ../knowledge-base
    .venv/Scripts/python -m app.kb.import --path ../knowledge-base --overwrite-drafts
    .venv/Scripts/python -m app.kb.import --path ../knowledge-base --publish --allow-todo   # CHỈ cho eval / demo

- Mặc định chỉ tạo **nháp**, không đè nháp admin đang soạn (trừ `--overwrite-drafts`).
- `--publish` công bố luôn; vẫn chặn tài liệu còn `todo` như thiết kế 3.3.
- `--allow-todo` (đi kèm `--publish`, không bao giờ là mặc định): cho công bố khi còn `todo`; phiên bản được đánh dấu
  `published_with_open_todo = true` để trang quản trị cảnh báo. Quyết định của người dùng 2026-11-03.
- Đồng thời nạp danh sách route (`_routes.yaml`) và câu hỏi gợi ý mặc định vào `kb_setting` nếu chưa có.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from sqlalchemy.orm import Session, sessionmaker

from app.db.models import KbSetting
from app.kb.parser import DocumentFormatError, find_document_files, parse_file
from app.kb.routes import load_routes_file
from app.kb.service import KbError, KnowledgeBaseService

DEFAULT_SUGGESTIONS = {
    "STUDENT": {
        "default": ["Làm sao đăng tin tìm gia sư?", "Phí đăng tin là bao nhiêu?", "Hợp đồng của tôi thế nào rồi?"],
        "/student/wallet": ["Nạp ví tối thiểu bao nhiêu?", "Thanh toán VNPay bị lỗi thì sao?"],
        "/student/contracts": ["Hủy hợp đồng thế nào?", "Kỳ học phí tiếp theo khi nào?"],
    },
    "TUTOR": {
        "default": ["Hồ sơ của tôi đã được duyệt chưa?", "Làm sao nộp chứng chỉ?"],
        "/tutor/certificates": ["Chứng chỉ bị từ chối thì làm gì?"],
    },
}


@dataclass
class ImportReport:
    actions: Counter = field(default_factory=Counter)
    published: int = 0
    published_with_open_todo: int = 0
    errors: list[str] = field(default_factory=list)

    def summary(self) -> str:
        parts = [f"{name}: {count}" for name, count in sorted(self.actions.items())]
        parts.append(f"công bố: {self.published} (còn todo: {self.published_with_open_todo})")
        parts.append(f"lỗi: {len(self.errors)}")
        return ", ".join(parts)


def import_settings(session_factory: sessionmaker[Session], routes_file: Path) -> None:
    """Ghi route cho phép và câu hỏi gợi ý mặc định nếu `kb_setting` chưa có (không đè chỉnh sửa của admin)."""
    routes = [{"path": route.path, "audience": list(route.audience)} for route in load_routes_file(routes_file)]
    with session_factory.begin() as session:
        for key, value in (("routes", routes), ("suggestions", DEFAULT_SUGGESTIONS)):
            if session.get(KbSetting, key) is None:
                session.add(KbSetting(key=key, value=value))


def import_directory(service: KnowledgeBaseService, root: Path, *, overwrite_drafts: bool = False,
                     publish: bool = False, allow_todo: bool = False) -> ImportReport:
    if allow_todo and not publish:
        raise ValueError("--allow-todo chỉ dùng cùng --publish")
    report = ImportReport()
    for path in find_document_files(root):
        try:
            parsed = parse_file(path)
        except DocumentFormatError as error:
            report.errors.append(f"{path.name}: {error}")
            continue
        saved = service.save_draft(parsed, origin="IMPORT", overwrite_draft=overwrite_drafts)
        report.actions[saved.action] += 1
        if not publish:
            continue
        try:
            version = service.publish(saved.document_id, allow_open_todo=allow_todo)
        except KbError as error:
            if error.code != "DRAFT_NOT_FOUND":  # không có nháp mới = đã công bố đúng nội dung này rồi
                report.errors.append(f"{parsed.doc_key}: {error} {error.details}")
            continue
        report.published += 1
        report.published_with_open_todo += int(version.published_with_open_todo)
    return report


def main() -> None:
    from app.cli import use_utf8_output

    use_utf8_output()
    from app.config import Settings
    from app.db.session import make_engine, make_session_factory
    from app.kb.tokens import load_token_counter
    from app.providers.factory import build_embedder

    parser = argparse.ArgumentParser(description="Nạp kho tri thức khởi tạo vào DB (dạng nháp)")
    parser.add_argument("--path", type=Path, default=Path("../knowledge-base"))
    parser.add_argument("--overwrite-drafts", action="store_true")
    parser.add_argument("--publish", action="store_true", help="Công bố ngay sau khi nạp")
    parser.add_argument("--allow-todo", action="store_true",
                        help="CHỈ cho eval/demo: cho công bố khi còn todo (đánh dấu published_with_open_todo)")
    arguments = parser.parse_args()

    settings = Settings()
    session_factory = make_session_factory(make_engine(settings.database_url))
    routes_file = arguments.path / "_routes.yaml"
    import_settings(session_factory, routes_file)
    allowed = {route.path for route in load_routes_file(routes_file)}
    service = KnowledgeBaseService(session_factory, load_token_counter(settings.tokenizer_path), lambda: allowed,
                                   build_embedder(settings))
    report = import_directory(service, arguments.path, overwrite_drafts=arguments.overwrite_drafts,
                              publish=arguments.publish, allow_todo=arguments.allow_todo)
    print(report.summary())
    for error in report.errors:
        print("  LỖI:", error)
    if arguments.allow_todo:
        print("CẢNH BÁO: đã công bố tài liệu còn câu hỏi todo (chỉ dùng cho eval / demo).")


if __name__ == "__main__":
    main()
