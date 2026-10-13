# 12. Cơ sở lý thuyết

Tài liệu này đối chiếu các quyết định thiết kế với lý thuyết về kiến trúc agent và RAG, để dùng cho chương cơ sở
lý thuyết và phần lập luận thiết kế trong báo cáo. Ký hiệu [BG §x] chỉ mục tương ứng trong bài giảng
*Advanced Agent Architectures — Harness & Kiến trúc* (AICB-P2T3, Ngày 16, VinUniversity); [MEM Sx] chỉ slide tương
ứng trong bài giảng *Memory Systems for Agents* (AICB-P2T3, Ngày 17, VinUniversity).

## 12.1. Harness: hiệu năng không nằm hết trong model

Khi năng lực model nền tảng đã đủ cao, khác biệt giữa các sản phẩm AI nằm ở **harness** — lớp code bao quanh model
[BG Mở đầu, §7.1]. Harness có ba "núm xoay" [BG §7.2]:

| Núm | Bản chất | Trong thiết kế |
|---|---|---|
| System prompt | Tĩnh; sửa hành vi, không sửa cơ chế | Prompt theo vai trò, có `prompt_version` ([04](./04-luong-hoi-dap.md) mục 4.4) |
| Tools | Tĩnh; mỗi tool là một khả năng và một bề mặt rủi ro | 15 tool chỉ-đọc mỗi vai trò, chọn động ≤ 6 ([05](./05-cong-cu-tra-cuu.md)) |
| Middleware | Lập trình được, có trạng thái, thấy được lịch sử, ngân sách, mẫu lặp | 6 hook ([02](./02-kien-truc.md) mục 2.3) |

Prompt và tool chỉ **mô tả** agent; middleware **can thiệp** lúc chạy: đọc trạng thái, chặn hành động, sửa ngữ
cảnh, dừng vòng lặp. Đó là lý do thiết kế đặt mọi quy tắc an toàn ở middleware.

## 12.2. Chọn mức kiến trúc: bắt đầu đơn giản

**ReAct** xen kẽ suy luận và hành động (Thought → Action → Observation) [BG §1.1; Yao et al., 2023]. Ba kiểu thất
bại: lỗi lan tỏa, vòng lặp vô hạn, không quay đầu [BG §1.2]. Nhiều tác vụ thực tế chỉ cần **retrieval + tools +
structured output**; một agent đơn hoặc workflow tuyến tính là đủ và thường tốt hơn vì ít thành phần, dễ debug, rẻ
và nhanh hơn. Chỉ nâng cấp khi có bằng chứng: quá tải tool, logic rẽ nhánh quá nhiều, cần chuyên môn hóa
[BG §1.3, §9.2].

Đối chiếu ma trận quyết định [BG §12.3] với bài toán trợ lý:

| Lớp | Khi nào dùng (bài giảng) | Trợ lý PTutor | Quyết định |
|---|---|---|---|
| ReAct thuần | Task ít bước | Lượt hỏi đáp: 0–3 lời gọi tool | **Nền tảng** |
| + critic | Cần tự sửa lỗi | Trích dẫn có thể gán sai nguồn | **Dùng**, chỉ cho câu trả lời cuối, thử lại ≤ 1 |
| + search (LATS, ToT) | High-stakes và cho phép rollback; tốn 3–5 lần compute | Câu hỏi ngắn, lợi ích không tương xứng | Không |
| + skills | Lặp theo domain, cần tích lũy | Kho tri thức truy xuất theo nhu cầu đóng vai trò tương tự | Dạng kho tri thức (12.4) |
| + plan file | Task dài, cần checkpoint | Một lượt vài giây | Không |
| + subagents | Subtask độc lập, song song | Không có | Không |

Ba kiểu thất bại của ReAct được xử lý ở harness: **lỗi lan tỏa** — viết lại câu hỏi + truy xuất trước khi gọi
model, kiểm chứng cuối; **vòng lặp vô hạn** — `LoopGuardMiddleware`, `BudgetMiddleware`, giới hạn cứng số vòng
[BG §7.8]; **không quay đầu** — thử lại có góp ý một lần, sau đó abstain thay vì cố đi tiếp.

## 12.3. Critic layer và kiểm chứng trích dẫn

**Reflexion** thêm Evaluator và Reflector quanh vòng lặp ReAct: sinh → chấm → rút bài học bằng ngôn ngữ tự
nhiên → thử lại, có giới hạn số lần [BG §2.1–2.3; Shinn et al., 2023]. Reflexion không phải kiến trúc riêng mà là
**critic layer**, tương ứng hook `after_model` [BG §2.8, §7.6].

Áp dụng có chọn lọc, theo bốn nguyên tắc thiết kế Evaluator [BG §2.5]:

| Nguyên tắc | Trong `CitationVerifierMiddleware` |
|---|---|
| Rubric tường minh | `SUPPORTED` / `PARTIAL` / `UNSUPPORTED` với định nghĩa cụ thể |
| Bắt buộc trích bằng chứng | Trường `evidence`; code kiểm tra là chuỗi con của đoạn nguồn |
| Verdict máy đọc được | JSON `{claim, verdict, evidence, reason}` |
| Tách ngữ cảnh với Actor | Verifier chỉ thấy khẳng định + đoạn nguồn, không thấy lập luận của Actor |

Tránh các cạm bẫy [BG §2.7]: *correlated failure* — prompt riêng, có thể dùng model khác; *reflection không đổi
chiến lược* — góp ý chỉ rõ khẳng định nào sai và vì sao; *retry không giới hạn* — `max_verify_retries = 1`;
*bài học mâu thuẫn* — không giữ "bài học" qua các lượt. Góp ý chỉ chứa thông tin cô đọng, không đưa lại transcript
hay dữ liệu thô [BG §2.6].

**Ba loại lỗi trích dẫn** [BG §11.2]: khẳng định không có nguồn, gán sai nguồn, nguồn bịa — mỗi loại cần một
cơ chế bắt riêng. Quy trình kiểm chứng: tách khẳng định → kiểm từng khẳng định với đúng đoạn được trích → **abstain**
khi thiếu bằng chứng thay vì bịa để lấp khoảng trống [BG §11.3]. Bảng ánh xạ ở [04](./04-luong-hoi-dap.md) mục 4.6.

## 12.4. Ngữ cảnh: model được thấy gì

- **Nạp kiến thức theo nhu cầu**: kiến thức chi tiết chỉ được nạp khi tác vụ chạm tới, thay vì nhồi tất cả vào
  ngữ cảnh [BG §4.1]. Kho tri thức + truy xuất là dạng này: mỗi lượt chỉ 6 đoạn liên quan; system prompt là phần
  "thường trực".
- **Chọn tool động**: tập tool đổi theo trạng thái để giảm quá tải lựa chọn [BG §7.4] → `ToolSelectionMiddleware`.
- **Loại bỏ có cấu trúc vs nén bằng LLM** [BG §8.1–8.2]: nén (compaction) tốn thêm một lời gọi model và có thể
  trôi nghĩa; loại bỏ theo luật thì rẻ và dự đoán được. Hội thoại hỗ trợ ngắn → chỉ dùng **cửa sổ trượt + bỏ kết
  quả tool cũ** (`MemoryMiddleware`) cùng trạng thái phiên có cấu trúc (mục 12.8); không dùng compaction.
- **Mật độ hơn đầy đủ** [BG §2.6]: lịch sử chỉ giữ câu hỏi và câu trả lời cuối; góp ý thử lại ngắn gọn.

## 12.5. RAG và mô hình embedding

**Retrieval-Augmented Generation** kết hợp truy xuất văn bản với sinh câu trả lời để giảm bịa và cập nhật kiến
thức mà không huấn luyện lại model (Lewis et al., 2020). Thiết kế dùng **truy xuất lai**: vector cho ngữ nghĩa,
full-text cho từ khóa chính xác, gộp bằng **Reciprocal Rank Fusion** (Cormack et al., 2009) rồi xếp lại bằng
cross-encoder.

**bge-m3** (Chen et al., 2024) là mô hình embedding đa ngôn ngữ ~568M tham số. Trên benchmark tiếng Việt
**VN-MTEB** (2025), nhóm Retrieval: bge-m3 đạt 39,84 — xếp ngay sau multilingual-e5-large-instruct (40,88) trong
nhóm mô hình cỡ ~500M; các mô hình điểm cao hơn (gte-Qwen2-1.5B-instruct 42,01, gte-Qwen2-7B-instruct 46,05) lớn
gấp 3–13 lần và thường cần GPU; bản tinh chỉnh riêng tiếng Việt từ bge-m3 (Vietnamese-Embedding, 34,18) không
vượt bản gốc. Với kho tri thức nhỏ, hẹp, có thêm full-text và rerank, bge-m3 (đã chạy sẵn trong `ai-service`) là
lựa chọn cân bằng; Recall@6 trên bộ câu hỏi của chính hệ thống được báo cáo ở thí nghiệm E1
([10](./10-danh-gia.md)); so sánh với model embedding khác để ở hướng phát triển.

## 12.6. Guardrails: prompt không phải biện pháp kiểm soát

Bề mặt tấn công mới của agent: chỉ dẫn chèn qua nội dung truy xuất, qua kết quả tool, và tool bị đầu độc
[BG §10.1]. **Phòng thủ nhiều lớp** gồm: kiểm tra đầu vào, kiểm tra đầu ra, thực thi quyền qua bản khai năng lực
tại lời gọi tool, chặn hành động không thể hoàn tác bằng phê duyệt của người, và cầu dao ngân sách / tần suất
[BG §10.2]. **Một quy tắc chỉ nằm trong system prompt không phải một lớp phòng thủ**; mọi tool cần có phạm vi,
mục allowlist và phân loại khả năng hoàn tác [BG §10.3]. Gating ở `wrap_tool_call` biến quy tắc thành bất biến mà
lời lẽ không thuyết phục phá được; harness tự chèn định danh người dùng để model không làm giả được [BG §7.4].

Áp dụng: [09](./09-bao-mat-va-quyen-rieng-tu.md) mục 9.1, [02](./02-kien-truc.md) mục 2.4,
[05](./05-cong-cu-tra-cuu.md) mục 5.2. Vì trợ lý không có hành động ghi, lớp "phê duyệt của người" không cần
thiết — đây là lựa chọn có chủ đích (QĐ7), giảm hẳn rủi ro của prompt injection.

## 12.7. Ngân sách, dấu vết và đánh giá

- **Budget accounting và trace** [BG §7.7]: mở hạn mức và root span ở `before_agent`, trừ dần ở các hook, chốt ở
  `after_agent`; mỗi lời gọi model / tool là một span con. Không có cặp hook này thì không trả lời được "một lượt
  tốn bao nhiêu" và không so sánh được hai cấu hình harness → bảng `turn_trace`.
- **Đánh giá trajectory** [BG §12.2]: không chỉ đúng / sai ở đáp án cuối mà cả việc chọn đúng tool, tự phục hồi
  khi lỗi, nằm trong ngân sách → chỉ số trajectory ở [10](./10-danh-gia.md) mục 10.2.
- **Không đo được thì không được thay đổi** [BG §5.4]: bộ câu hỏi mẫu là điều kiện tiên quyết cho mọi thay đổi;
  phải có tập hồi quy; trace chuẩn hóa từ middleware là nền để chấm. Cảnh báo "metric yếu thì hệ thống tối ưu theo
  metric, không theo mục tiêu" → đo riêng chất lượng verifier và chấm tay ngẫu nhiên.
- **Endure – Excel – Evolve** [BG §5.5]: an toàn trước, không thoái lui, rồi mới cải thiện → thứ tự xét ở cổng
  hồi quy ([10](./10-danh-gia.md) mục 10.3). Vòng "câu hỏi chưa trả lời được → admin bổ sung tài liệu" là cải tiến
  ở tầng **kinh nghiệm / kiến thức của môi trường** [BG §5.2], do con người kiểm soát; agent không tự sửa kho tri
  thức hay guardrail, và guardrail nằm ngoài vùng admin chỉnh sửa được.

## 12.8. Bộ nhớ của agent

**Agent mặc định không có trạng thái**: mỗi lời gọi LLM là độc lập; muốn "nhớ" thì lớp ứng dụng phải lưu và nạp lại
[MEM S2]. Ngữ cảnh là tài nguyên hữu hạn, cần tổ chức thành các lớp có thứ tự ưu tiên khi cắt; lớp chính sách /
system prompt **không bao giờ bị cắt** để hành vi nhất quán; phần dành cho "quá khứ" chỉ nên chiếm phần nhỏ (quy
tắc kinh nghiệm ~20%) vì thông tin hiện tại có giá trị hơn — và tỉ lệ này phải được đo trên chính bài toán, không áp
dụng máy móc [MEM S4–S5]. → Bảng ngân sách theo lớp và thứ tự cắt ở [04](./04-luong-hoi-dap.md) mục 4.10.

**Bốn loại bộ nhớ** [MEM S6]: ngắn hạn (working), dài hạn (declarative: sở thích, fact, quy tắc), trải nghiệm
(episodic) và ngữ nghĩa (semantic: tri thức chung). Không có thiết kế phù hợp mọi bài toán; chỉ thêm loại bộ nhớ khi
bài toán cần [MEM S30]. Đối chiếu:

| Loại | Lý thuyết | Trợ lý PTutor |
|---|---|---|
| Ngắn hạn | Chiến lược tốt nhất cho production: system + tóm tắt + K lượt gần nhất; buffer thuần dễ bị "kẹt" khi người dùng hỏi lặp một chi tiết [MEM S7] | Cửa sổ 6 lượt + **trạng thái phiên có cấu trúc** (`focus`); tóm tắt bằng LLM để hướng phát triển |
| Dài hạn | Lưu fact / sở thích có TTL, nạp vào đầu phiên [MEM S8] | **Không lưu**: dữ liệu người dùng nằm ở DB nghiệp vụ, lấy mới qua tool. Thông tin chỉ đúng tại thời điểm truy xuất không nên lưu dài hạn [MEM S6 mở rộng] |
| Trải nghiệm | Log (task, trajectory, outcome, reflection), truy hồi theo độ tương đồng [MEM S11] | **Không làm**: câu hỏi hỗ trợ thường độc lập, lợi ích không tương xứng chi phí và rủi ro |
| Ngữ nghĩa | Embedding + vector DB cho tri thức chung [MEM S12] | Kho tri thức + truy xuất lai |

**Ghi chú có cấu trúc thay cho tóm tắt văn xuôi**: tóm tắt có thể mơ hồ hơn bản gốc, nên ghi chú phải ưu tiên trạng
thái, quyết định, điều còn mở, ràng buộc; chỉ nén khi gần chạm giới hạn hoặc phiên quá dài, không nén mọi lúc
[MEM S19–S20]. → `session_state.focus` do code cập nhật; với hội thoại hỗ trợ ngắn, chưa cần nén bằng LLM.

**Nguồn gốc và xung đột**: mỗi bản ghi bộ nhớ cần nguồn, thời điểm; tách bạch yêu cầu người dùng, quy tắc hệ thống và
suy luận của agent; chính sách luôn thắng sở thích người dùng [MEM S4, S18, S24]. → `focus` chỉ nhận dữ liệu từ tool
(có `source`, `turn`); dữ liệu hệ thống thắng lời khẳng định của người dùng.

**Không để agent tự ghi bộ nhớ bền vững**: bộ nhớ bền vững + chỉ dẫn chèn vào có thể thành "đầu độc bộ nhớ" kéo dài
qua nhiều phiên; ghi bền vững phải có allowlist, nguồn gốc, và thay đổi quan trọng phải qua người duyệt [MEM S24].
Bài giảng cũng nêu hướng agent tự bổ sung tri thức (incremental knowledge growth) [MEM S12] — thiết kế **cố ý không
áp dụng**: chỉ admin ghi kho tri thức, có nguồn gốc và phiên bản (khôi phục được).

**Kho tri thức đã biên tập**: thay vì mỗi lần tổng hợp lại từ tài liệu thô, tri thức được biên tập thành các trang
theo thực thể / khái niệm, có nguồn gốc và được cập nhật dần [MEM S25–S26]. Kho tri thức của trợ lý (tài liệu theo chủ
đề, glossary trạng thái, `source_refs`, phiên bản, admin biên tập) đi theo hướng này ở quy mô nhỏ.

**Quyền riêng tư ngay từ thiết kế** [MEM S15, S18]: tối thiểu hóa dữ liệu, giới hạn mục đích, thời hạn lưu, xóa có
kiểm chứng trên mọi nơi lưu (dữ liệu bị chia mảnh dễ xóa sót), cách ly theo người dùng — gán nhầm bộ nhớ sang người
dùng khác là lỗi nghiêm trọng. → [09](./09-bao-mat-va-quyen-rieng-tu.md) mục 9.3; hàm xóa duy nhất trên 6 bảng +
`deletion_log`.

**Đo giá trị của bộ nhớ**: so sánh có / không có bộ nhớ trên các hội thoại nhiều lượt, phân tích tỉ lệ truy hồi
trúng và phân bổ token [MEM S27, S29]. → Thí nghiệm E4 trên bộ hội thoại nhiều lượt ([10](./10-danh-gia.md) mục
10.5) và số token theo lớp trong trace; so sánh đầy đủ các cấu hình bộ nhớ để ở hướng phát triển.

## 12.9. Tài liệu tham khảo

- Bài giảng *Advanced Agent Architectures — Harness & Kiến trúc*, AICB-P2T3, Ngày 16, Chương 4, VinUniversity.
- Bài giảng *Memory Systems for Agents*, AICB-P2T3, Ngày 17, Chương 4, VinUniversity.
- Yao, S. et al. (2023). *ReAct: Synergizing Reasoning and Acting in Language Models*. ICLR 2023.
- Shinn, N. et al. (2023). *Reflexion: Language Agents with Verbal Reinforcement Learning*. NeurIPS 2023.
- Lewis, P. et al. (2020). *Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks*. NeurIPS 2020.
- Cormack, G. V., Clarke, C. L. A., Büttcher, S. (2009). *Reciprocal Rank Fusion Outperforms Condorcet and
  Individual Rank Learning Methods*. SIGIR 2009.
- Chen, J. et al. (2024). *BGE M3-Embedding: Multi-Lingual, Multi-Functionality, Multi-Granularity Text Embeddings
  Through Self-Knowledge Distillation*. arXiv:2402.03216.
- *VN-MTEB: Vietnamese Massive Text Embedding Benchmark* (2025). arXiv:2507.21500.
