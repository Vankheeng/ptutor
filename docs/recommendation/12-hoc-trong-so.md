# 12. Học trọng số trực tuyến (L1)

> 💡 **File này nói về gì:** Thiết kế (đã lập trình 20/10/2026) cho việc hệ thống **tự chỉnh dần các trọng số** — mức quan trọng của nội dung, giá, lịch… khi tính điểm — từ phản hồi của người dùng, thay vì dùng mãi con số nhóm tự đặt. Đây là phần nặng toán nhất; giải thích trực giác trước ở [00](./00-doc-truoc.md) mục 4.9.

> Trạng thái: viết ngày 13/10/2026; **đã lập trình** 20/10/2026 (Bước 19–23: `WeightProvider`, `LinearBayesModel`,
> `WeightLearningJob`, API quản trị), mặc định chế độ `SHADOW`. Thí nghiệm học trên dữ liệu mô phỏng: 09, mục 9.9 và
> `ket-qua/`.

## 12.1. Mục tiêu và ý tưởng

> 💡 Trọng số nhóm đặt trở thành **prior** — điểm xuất phát. Chưa có dữ liệu thì hệ thống chạy y hệt bản hiện tại; có dữ liệu thì trọng số dịch dần về phía làm người dùng phản ứng tốt hơn. Phía a (người tìm có thích không) là **học tăng cường dạng contextual bandit**: hệ thống chọn bộ trọng số → người dùng phản hồi → học, và phải chủ động thử. Phía b (phía kia có nhận không) là **học có giám sát**: đã có sẵn đáp án nhận / từ chối trong bảng đơn, không cần thử.

Các trọng số trong a, a′, b, b′ (06) là **giá trị nhóm tự đặt** cho giai đoạn khởi đầu lạnh. L1 biến
chúng thành **điểm xuất phát (prior)** của một mô hình học: khi hệ thống chạy thật, mô hình cập nhật
trọng số từ phản hồi của người dùng. Code và file cấu hình không đổi; trọng số học được là **dữ liệu**,
lưu trong database.

| | Trước L1 | Với L1 |
|---|---|---|
| Trọng số lấy từ | `RecommendationSettings` (cố định) | Bản học được trong database; chưa có thì lấy `RecommendationSettings` |
| Vai trò của trọng số tự đặt | Giá trị dùng mãi mãi | Prior + mốc giới hạn an toàn |
| Khi chưa có dữ liệu | — | Hành vi **giống hệt** bản cố định |
| Khi có dữ liệu | Không đổi | Trọng số dịch dần về phía làm người dùng phản ứng tốt hơn |

**Cách gọi trong báo cáo:** *học tăng cường dạng contextual bandit* (phía a, dùng Linear Thompson
Sampling) kết hợp *hồi quy Bayes trực tuyến* (phía b). Không gọi là "deep reinforcement learning":
bài toán chỉ có một bước (hiển thị → phản hồi), không có chuỗi trạng thái chuyển tiếp, và dữ liệu ít —
contextual bandit là cách đặt bài toán học tăng cường đúng cho trường hợp này (11, mục 11.6).

**Phần nào là học tăng cường, phần nào không:**

| Thành phần RL | Phía a (`A_S2T`, `A_T2S`) |
|---|---|
| Tác tử | Bộ xếp hạng |
| Ngữ cảnh | Nhu cầu tìm kiếm + vector đặc trưng của các ứng viên |
| Hành động | Chọn bộ trọng số → thứ tự hiển thị |
| Phần thưởng | Hành động của người tìm (12.4) |
| Chính sách | Phân phối hậu nghiệm của trọng số, rút mẫu Thompson |
| Khám phá – khai thác | Chưa chắc → thử nhiều; chắc rồi → dùng bộ tốt nhất |
| Học từ tương tác | Chỉ có phản hồi cho những gì hệ thống **đã chọn** hiển thị |

- **Phía a là học tăng cường** (contextual bandit — học tăng cường một bước; thiếu so với MDP đầy đủ là chuỗi
  trạng thái chuyển tiếp).
- **Phía b là học có giám sát trực tuyến**: nhãn nhận / từ chối có sẵn trong bảng đơn, không phụ thuộc hệ
  thống chọn gì, nên không cần — và không nên — khám phá (12.6).

**Giữ nguyên:** lọc cứng, cấu trúc `R = 2ab/(a+b)`, `score = R × (0,7 + 0,3·Q) × G`, cách tính từng đặc
trưng, rerank, slot khám phá. L1 chỉ thay **trọng số gộp**.

## 12.2. Tham số nào được học, tham số nào giữ cố định

> 💡 Chỉ học các **trọng số gộp** của a và b; còn lại giữ cố định có lý do — ví dụ điểm uy tín Q là quy tắc công khai phải giải thích được với gia sư, hệ số chống dồn đơn là chính sách cho cả thị trường, thang điểm phản hồi chính là thước đo nên không thể vừa làm thước vừa đem đi học.

### Được học — bốn mô hình

> 💡 Bốn bộ trọng số được học riêng: a và b cho chiều học sinh tìm gia sư (`A_S2T`, `B_S2T`), a′ và b′ cho chiều gia sư tìm học viên (`A_T2S`, `B_T2S`). Công thức b hiện tại được viết lại dạng "dàn phẳng" (một tổng có trọng số) cho dễ học, mà với trọng số ban đầu vẫn cho đúng kết quả cũ. Ví dụ b_fit = trung bình 3 thành phần = mỗi thành phần × 1/3, rồi × 0,5 → mỗi thành phần có trọng số 1/6.

| Mô hình | Trọng số | Prior (= giá trị hiện tại) | Tín hiệu học |
|---|---|---|---|
| `A_S2T` | a cơ sở: sem, need, price, style, sched, area, segment | 0,30 · 0,20 · 0,20 · 0,10 · 0,10 · 0,05 · 0,05 | Học sinh xem / tym / liên hệ / gửi đơn |
| `A_T2S` | a′ cơ sở: sem, price, need, segment, sched, area | 0,30 · 0,25 · 0,20 · 0,10 · 0,10 · 0,05 | Gia sư xem / tym / gửi đề nghị |
| `B_S2T` | b trước `b_cap`: hist, fit_segment, fit_sched, fit_price | 0,5 · 1/6 · 1/6 · 1/6 | Gia sư **nhận / từ chối** đơn |
| `B_T2S` | b′ trước `b′_cap`: hist, quality, style, price | 0,5 · 0,20 · 0,15 · 0,15 | Học sinh **nhận / từ chối** đề nghị |

Prior của `B_S2T`, `B_T2S` là dạng "dàn phẳng" của công thức hiện tại:

```
b  = (0,5·b_hist + 0,5·b_fit) × b_cap,   b_fit  = trung bình(fit_segment, fit_sched, fit_price)
   = (0,5·b_hist + 1/6·fit_segment + 1/6·fit_sched + 1/6·fit_price) × b_cap
b′ = (0,5·b′_hist + 0,5·(0,4·Q + 0,3·f_style + 0,3·f_price)) × b′_cap
   = (0,5·b′_hist + 0,20·Q + 0,15·f_style + 0,15·f_price) × b′_cap
```

Thành phần fit bị thiếu được thay theo đúng cách công thức hiện tại đang làm, để dạng dàn phẳng với trọng
số prior cho **đúng** giá trị hiện tại — khi chưa học gì, kết quả không đổi:
- **b (`B_S2T`)**: thay bằng **trung bình các thành phần có mặt** (không có thành phần nào → `p0`), vì `b_fit`
  hiện là trung bình các thành phần có dữ liệu.
- **b′ (`B_T2S`)**: `Q` luôn có; `f_style`, `f_price` thiếu được thay bằng **giá trị trung tính 0,5**
  (`tutor-willingness.neutral`), vì `b′_fit` hiện vẫn dùng 0,5 cho phần thiếu. (Quyết định 20/10/2026: giữ khác
  b để kết quả không đổi.)

Phía a dùng đúng quy tắc thiếu dữ liệu ở 06, mục 6.5: đặc trưng thiếu nhận giá trị trung tính 0,5, nên
`a_cơ_sở = Σ_k w_k·x_k` với `x_k = f_k` hoặc 0,5. Đây cũng chính là vector đặc trưng đưa vào mô hình học.

### Giữ cố định — có lý do

> 💡 Bảng từng nhóm tham số không được học và lý do. Kèm một việc phải làm trước: gom các hằng số đang viết cứng trong code vào file cấu hình.

| Tham số | Lý do không học |
|---|---|
| Điểm chất lượng Q (cách tính `recommendation_score`, gồm phạt khiếu nại có lỗi) | Quy tắc nghiệp vụ, công khai và giải thích được với gia sư; tự học sẽ làm điểm đổi mà không lý giải được |
| `b_cap`, λ = 0,5 | Chính sách cấp **thị trường** (chống dồn đơn). Học theo từng lượt sẽ tối ưu cho từng đơn và làm mất tác dụng chống dồn (Su và cộng sự, 2022) |
| Gate G (0,4 / 0,5), hệ số chất lượng 0,7 / 0,3 | Chính sách sản phẩm; dữ liệu phản hồi không đủ để phân biệt tác động |
| Trọng số tín hiệu (xem 1, tym 2…) và thang thưởng của L1 | Chính là **định nghĩa phần thưởng** — không thể vừa làm thước đo vừa đem đi học |
| Kernel trong `b_hist` (0,5 / 0,3 / 0,2), `b′_hist` (0,5 / 0,5) | Nằm trong một tỉ số (làm trơn Bayes), không tuyến tính theo trọng số; kiểm chứng bằng phân tích độ nhạy (09, mục 9.9) |
| `w_hist` (0 → 0,15), `rerank-weight` 0,7 | Giữ trong bản đầu cho đơn giản; có thể mở rộng (12.13) |
| Điểm thành phần trong đặc trưng (khớp tag cha 0,6, cùng tỉnh 0,6…), α, p0 | Định nghĩa miền / tham số thống kê; kiểm chứng bằng phân tích độ nhạy |
| Ngưỡng vận hành (lọc giá 1,5×, slot 5 và 12, nới tiêu chí, thông báo…) | Quyết định sản phẩm và hiệu năng, không phải trọng số xếp hạng |

**Việc cần làm trước (tái cấu trúc, không đổi kết quả) — đã làm 20/10/2026:** chuyển các hằng số đang viết
cứng vào `RecommendationSettings` — `0,5·hist + 0,5·fit` (`willingness.history-share`,
`tutor-willingness.history-share`), `b′_fit` 0,4 / 0,3 / 0,3 (`tutor-willingness.fit-weights.*`), kernel
0,5 / 0,3 / 0,2 (`willingness.kernel.*`) và 0,5 / 0,5 (`tutor-willingness.kernel.*`), `f_hist` 0,6 / 0,4
(`history.cosine-share`, `tag-share`), trọng số tín hiệu H1 (`history.signal-weights.*`), các điểm thành phần
trong `FeatureCalculator` (`features.*`). Bảng đầy đủ ở 10, mục 10.1. Đây cũng là điều kiện để chạy phân tích độ
nhạy.

> 💡 Bước tái cấu trúc này không đổi kết quả nào: test `RankingRegressionTest` chấm 600 cặp ngẫu nhiên (có hạt giống
> cố định, đủ các trường hợp thiếu dữ liệu) bằng code **trước** khi sửa, lưu điểm vào file, rồi kiểm tra code sau
> khi sửa cho đúng các điểm đó với sai số ≤ 10⁻⁹.

## 12.3. Mô hình toán

> 💡 **Hồi quy tuyến tính Bayes**: giả định phản hồi r ≈ tổng có trọng số của các đặc trưng x, cộng nhiễu. θ là vector trọng số cần học. Thay vì nhớ hết dữ liệu, chỉ cần giữ hai bảng số tích lũy **A** (ma trận) và **c** (vector), mỗi quan sát mới cộng thêm vào; trọng số ước lượng μ = A⁻¹c. **κ** = trọng số nhóm đặt "đáng giá" bao nhiêu quan sát (50 từ 27/10/2026, trước là 200): lúc đầu μ gần như bằng prior, phải có vài chục quan sát mới kéo lệch được. Σ đo mức chưa chắc chắn, càng nhiều dữ liệu càng nhỏ.

Mỗi mô hình là **hồi quy tuyến tính Bayes** trên vector đặc trưng `x` (thêm hệ số chặn `x₀ = 1`):

```
phản hồi r ≈ θ₀ + θ · x + nhiễu,          nhiễu ~ N(0, ν²)
prior      θ ~ N(θ_prior, A₀⁻¹)            θ_prior = (0, β·w_prior)  — hệ số chặn có prior 0, rất yếu
```

- **Hệ số chặn** là mức phản hồi nền (dù ứng viên thế nào vẫn có một xác suất bấm nhỏ). Prior của nó rất yếu:
  độ mạnh riêng `κ₀ = learning.intercept-prior-strength` (mặc định 1), để các trọng số không phải "gánh" mức nền.
- **β đưa prior về thang phần thưởng:** w có tổng 1 còn phần thưởng có thang riêng. β = Cov(r, w·x) / Var(w·x)
  (có trọng số mẫu, tối thiểu 0,01; không có biến thiên thì 1) đo một lần trên lô dữ liệu đầu tiên, lưu ở
  `evaluation.priorScale` của version đầu và mọi version sau dùng lại. Hướng của prior vẫn là trọng số nhóm đặt.
  Version lưu trước 21/10/2026 không có trường này: coi β = 1, đúng cách chúng đã được tính.

Viết ở dạng "thống kê đủ" để cập nhật dần và lưu được:

```
A₀ = diag(κ₀, κ, …, κ)    c₀ = A₀·θ_prior
A  = A₀ + Σ_i s_i · x_i x_iᵀ
c  = c₀ + Σ_i s_i · r_i · x_i
μ  = A⁻¹ c                (trung bình hậu nghiệm)
Σ  = ν² · A⁻¹             (hiệp phương sai hậu nghiệm)
```

- `κ` (`learning.prior-strength`) = prior "đáng giá" bao nhiêu quan sát. κ lớn → tin trọng số tự đặt lâu
  hơn. Mặc định 50 (từ 27/10/2026; trước là 200), chọn bằng thí nghiệm mô phỏng (13 mục 13.7).
- `s_i` = trọng số mẫu để giảm thiên lệch vị trí (12.5).
- Hệ số chặn hấp thụ mức phản hồi nền (ví dụ ai cũng ít bấm), **không** dùng khi xếp hạng.
- Kích thước: 8×8 (`A_S2T`), 7×7 (`A_T2S`), 5×5 (`B_*`). Nghịch đảo / phân rã Cholesky viết tay trong Java,
  không cần thư viện.

**Quên dần (tuỳ chọn):** trước mỗi lần cập nhật, `A ← A₀ + γ·(A − A₀)`, `c ← c₀ + γ·(c − c₀)` với
`γ = learning.forgetting` (mặc định 0,99 từ 27/10/2026; 1 = không quên). γ áp một lần trước mỗi lần cập nhật, tức
mỗi đêm khi chạy thật: với 0,99, dữ liệu của khoảng 70 đêm trước còn một nửa sức nặng.

### Từ θ sang trọng số dùng được

> 💡 Trọng số học ra có thể âm, lệch thang hoặc đổi quá nhanh, nên qua các bước an toàn: bỏ hệ số chặn, đưa về **cùng thang** với trọng số (phần âm tính là 0, chia cho tổng); rồi chọn bộ trọng số **gần nhất** với kết quả đó mà vẫn có tổng bằng 1, mỗi trọng số trong khoảng 0,5–2 lần giá trị nhóm đặt và đổi tối đa 0,05 so với bản trước. Ví dụ prior của giá là 0,20, mô hình muốn 0,45 → không quá 0,40; nếu bản trước là 0,20 thì lần này chỉ được lên 0,25 — các giới hạn này đúng trên **trọng số cuối cùng**.

θ học được có thể âm, lệch thang hoặc thay đổi quá nhanh. Trước khi dùng:

```
1. Bỏ hệ số chặn θ₀; đưa về thang trọng số: v_k = max(0, θ_k) / Σ max(0, θ_j)
2. Khoảng cho phép: L_k = max(max(0,01; 0,5·w_prior_k), w_k(bản trước) − max-step)
                    U_k = min(2·w_prior_k,            w_k(bản trước) + max-step)
3. w = điểm gần v nhất có Σ w = 1 và L ≤ w ≤ U:  w_k = clip(v_k + t, L_k, U_k), t chọn sao cho Σ w = 1
   (tổng tăng theo t, tìm bằng chia đôi). Không thoả được với giới hạn bước thì bỏ giới hạn bước;
   vẫn không được (prior quá lệch) thì kẹp rồi chia cho tổng.
```

Tổng bằng 1 giữ a, b trong [0, 1] để trung bình điều hoà có nghĩa.

**Sửa 21/10/2026:** bản đầu kẹp θ **thô** rồi mới chuẩn hoá. θ của hồi quy không cùng thang với trọng số nên
biên kẹp bị chạm ở mọi lần cập nhật, và bước chuẩn hoá phá giới hạn bước (bước thật tới 0,085). Thí nghiệm học
mô phỏng (09 mục 9.9, `ket-qua/`) phát hiện; người dùng chọn chuẩn hoá trước rồi mới kẹp. Chỉ báo `clamped` của
`WeightLearningService` cũng so biên trên θ đã đưa về thang trọng số.

Kẹp quanh prior nghĩa là trọng số tự đặt vẫn quyết định **vùng hợp lệ**: không đặc trưng nào bị mô hình
"tắt hẳn" hay chiếm toàn bộ điểm. Biên `[0,5×; 2×]` là tham số (`learning.min-ratio`, `max-ratio`).

**Ghi chú triển khai (20/10/2026):** `LinearBayesModel` (lớp toán thuần, không Spring): `fromPrior`,
`fromStatistics` (tiếp tục từ A, c đã lưu), `forget(γ)`, `update(x, r, s)`, `mean()` = giải A μ = c bằng phân rã
Cholesky viết tay, `sample(random, ρ, ν)` = μ + ρ·ν·L⁻ᵀz với A = L Lᵀ, z ~ N(0, I) (hiệp phương sai ρ²ν²A⁻¹),
`project(θ, prior, previous, …)` theo đúng bốn bước trên. Vì chuẩn hoá làm sau giới hạn bước, một trọng số sau cùng
có thể lệch bản trước hơn `max-step` một chút (tổng trước chuẩn hoá nằm trong 1 ± số đặc trưng × `max-step`).

## 12.4. Phía a: Linear Thompson Sampling

> 💡 Phía a phải chủ động thử, vì tin không được hiển thị thì không bao giờ có phản hồi. Mỗi lần tìm, hệ thống **rút ngẫu nhiên** một bộ trọng số quanh μ (θ̃ ~ N(μ, ρ²Σ): phân phối chuẩn, càng chưa chắc thì rút càng xa), qua bốn bước an toàn, rồi xếp hạng bằng bộ đó. **ρ** chỉnh mức thử; ρ = 0 là luôn dùng μ (tham lam). Cùng một người trong cùng một ngày nhận cùng bộ trọng số, để tìm lại không bị đảo lộn.

Khác phía b, phản hồi phía a **phụ thuộc chính thứ tự hệ thống hiển thị**: ứng viên không được hiển thị
thì không bao giờ có phản hồi. Nếu luôn dùng trọng số tốt nhất hiện tại, mô hình có thể mắc kẹt ở một
lời giải chưa tốt. Thompson sampling giải quyết bằng cách **thử nghiệm có kiểm soát**:

```
Mỗi lần tìm (khi mô hình ở chế độ ACTIVE):
  θ̃ ~ N(μ, ρ²·Σ)                            — rút một mẫu từ phân phối hậu nghiệm
  w̃ = project(θ̃)                            — kẹp, giới hạn bước, chuẩn hoá (12.3)
  a_cơ_sở = Σ_k w̃_k · x_k                    — xếp hạng như cũ với w̃
  Ghi w̃ vào log của lần tìm
```

- Lúc đầu Σ lớn → các mẫu khác nhau nhiều → hệ thống thử nhiều bộ trọng số. Dữ liệu tăng → Σ co lại →
  mẫu tiến về μ, tức tự chuyển từ khám phá sang khai thác.
- `ρ` (`learning.exploration-scale`, mặc định 0,5 từ 27/10/2026, trước là 1) điều chỉnh mức khám phá; `ρ = 0` → luôn dùng μ (tham lam).
- **Ổn định trải nghiệm:** hạt giống ngẫu nhiên = băm (người dùng, ngày), nên cùng một người trong cùng
  một ngày nhận cùng bộ trọng số — tìm lại không bị đảo thứ tự lung tung. Lật trang dùng thứ tự đã lưu
  theo `logId` như hiện tại.
- Các luồng nền (thông báo chủ động C7, `RerankPrefetchJob`, nới tiêu chí C6) dùng **μ**, không rút mẫu.
- Phân biệt với slot khám phá R6: R6 khám phá **ứng viên** (công bằng hiển thị); L1 khám phá **trọng số**.
  Hai cơ chế độc lập, cùng dựa trên Thompson sampling.

**Ghi chú triển khai (20/10/2026).**

> 💡 Mẫu Thompson được rút một lần cho mỗi (người dùng, ngày, mô hình, phiên bản) và giữ trong bộ nhớ, nên mọi ứng viên của một lần tìm — và cả log — dùng đúng một bộ trọng số. Các luồng nền được đánh dấu "dùng μ" ngay tại điểm vào, không phải sửa từng calculator.

- `WeightProvider.weightsFor(model, userId)`: phía a hỏi theo người tìm (`QueryContext.studentUserId`,
  `SupplyContext.tutorUserId`); phía b luôn `weights(model)` = μ. Hạt giống = băm (người dùng, ngày theo `Clock` của
  ứng dụng, mô hình). Mẫu qua `project` với "bản trước" là μ của phiên bản đang phục vụ.
- Luồng nền: `WeightProvider.withMeanWeights(…)` (phạm vi theo luồng xử lý, kiểu `LocaleContextHolder`) bọc
  `MatchAlertService` (C7, `matchPosition`), `RerankPrefetchJob` và lượt xếp hạng lại của `RelaxationService` (C6).
- Log: `query_context.weights.A` ghi đúng bộ đã dùng (`sampled = true` khi là mẫu), `version` của phiên bản đang
  phục vụ, `mode` là chế độ cấu hình (`OFF` / `SHADOW` / `ACTIVE`).

### Phần thưởng phía a

> 💡 Mỗi tin đã hiển thị cho một **điểm thưởng r** theo hành động mạnh nhất của người tìm trong 7 ngày: không làm gì 0, xem 0,3, tym 0,6, liên hệ 0,8, gửi đơn 1,0, bấm "bỏ qua" −0,2. Ví dụ người dùng xem rồi gửi đơn → r = 1,0. Chỉ tính hành động của người tìm; việc phía kia nhận hay không thuộc phía b.

Mỗi ứng viên đã hiển thị trong một lần tìm là một quan sát `(x, r)`. `r` = mức **cao nhất** người tìm đạt
được với ứng viên đó trong **7 ngày** kể từ lần tìm, cộng / trừ các điều chỉnh:

| Tín hiệu | r | Nguồn | Mức |
|---|---|---|---|
| Không làm gì | 0 | — | Bắt buộc |
| Xem chi tiết | 0,3 | `recommendation_click_logs`: `VIEWED`, `CLICKED` | Bắt buộc |
| Tym | 0,6 | click log `FAVORITED` | Bắt buộc |
| Liên hệ | 0,8 | click log `CONTACTED` | Bắt buộc |
| Gửi đơn (S→T) / gửi đề nghị (T→S) | 1,0 | `student_tutor_requests` / `tutor_student_requests` của người tìm vào đúng tin đó, tạo trong 7 ngày sau lần tìm (enum `RecommendationAction` không có hành động này — lấy từ bảng đơn) | Bắt buộc |
| Bấm "bỏ qua" | **−0,2** (thay mức cao nhất nếu không có tín hiệu dương nào) | click log `DISMISSED` | Bắt buộc |
| Xem lại ở ≥ 2 ngày khác nhau | +0,1 (tối đa 1,0) | nhiều dòng `VIEWED` | Tuỳ chọn |
| Bỏ yêu thích sau khi đã tym | mức tym → 0,3 | `favorite_tutors` bị xoá trong 7 ngày | Tuỳ chọn |
| Bấm vào thông báo ghép đôi (C7) | như "Xem chi tiết" | thông báo phải mang mã alert / `logId` | Tuỳ chọn |
| Rời trang chi tiết < 5 giây | xem → 0,1 | **cần thêm sự kiện từ frontend** (chưa có) | Tuỳ chọn |

Việc phía kia nhận hay từ chối thuộc về b. **Kết quả dài hạn** (hợp đồng đủ N buổi, hoàn thành hoặc gia hạn
qua `renewed_from_contract_id`, đánh giá ≥ 4 sao; ngược lại huỷ hợp đồng, khiếu nại có `fault_party` chỉ gia sư
hoặc học sinh có lỗi) hiếm và đến chậm 1–3
tháng: dùng làm **thước đo đánh giá**; dùng làm phần thưởng phụ (cửa sổ 60 ngày, trọng số nhỏ) là mở rộng
(12.13).

**Bốn điều kiện để một tín hiệu được dùng:** (1) truy được về lần đề xuất (`logId`, vị trí); (2) không quá
chậm; (3) đủ thường xuyên; (4) không vòng tròn — không dùng làm phần thưởng chính đại lượng đã là đặc trưng.

Phần thưởng phía a **chỉ gồm hành động của người tìm** — để mỗi mô hình học đúng đại lượng nó biểu diễn
(a = người tìm có quan tâm không).

## 12.5. Thiên lệch vị trí

> 💡 Tin ở trên được bấm nhiều hơn chỉ vì đứng trên. Ba cách bù: chỉ học từ phần danh sách người dùng có khả năng đã đọc (tới vị trí tương tác thấp nhất + 2); tin ở slot khám phá được tính nặng gấp đôi vì vị trí của chúng không do điểm quyết định; và (nâng cao) **IPW** — nhân mỗi quan sát với nghịch đảo xác suất được nhìn thấy, ví dụ vị trí chỉ 25% người đọc tới thì một lượt bấm ở đó tính bằng 4.

Ứng viên ở trên được bấm nhiều hơn chỉ vì đứng trên. Ba lớp xử lý:

1. **Chỉ lấy phần đã được nhìn:** với mỗi lần tìm có ít nhất một tương tác, chỉ lấy các ứng viên ở vị trí
   ≤ vị trí tương tác thấp nhất + 2 (giả thuyết người dùng đọc từ trên xuống). Lần tìm không có tương tác
   nào bị bỏ (không biết người dùng có đọc hay không).
2. **Ưu tiên slot khám phá:** ứng viên ở slot R6 (`isExploration`) được đặt ở vị trí không do điểm quyết
   định, nên ít thiên lệch; nhân `s_i = learning.exploration-sample-weight` (mặc định 2).
3. **Trọng số nghịch đảo xác suất được nhìn (IPW) — nâng cao:** `s_i = 1 / P(nhìn tới vị trí k)`, với
   `P` ước lượng từ tỉ lệ tương tác theo vị trí trên slot khám phá; cắt trên ở 10 để tránh phương sai lớn.
   Bật bằng `learning.ipw.enabled`.

**Ghi chú triển khai (20/10/2026) — dữ liệu học phía a.**

> 💡 `LearningDataRepository.searchFeedback` đọc các lần tìm trong một khoảng thời gian, gom mọi việc người tìm đã làm với từng tin trong 7 ngày sau lần tìm, rồi chỉ giữ phần danh sách họ có khả năng đã đọc. Mỗi tin cho một quan sát (đặc trưng x lấy đúng từ log, điểm thưởng r, trọng số mẫu s).

- Nguồn: `recommendation_logs` (chiều theo mô hình) + `recommendation_click_logs` của log đó + đơn / đề nghị người
  tìm gửi vào đúng tin đó trong cửa sổ `reward-window-days`. Một đơn có thể được tính cho nhiều lần tìm trước nó
  trong cửa sổ.
- Chỉ dùng các tín hiệu **"Bắt buộc"** của bảng phần thưởng 12.4; các cờ tuỳ chọn (`rewards.repeat-view`,
  `unfavorite`, `alert-click`, `bounce`) và IPW (`ipw.enabled`) đã có tham số nhưng **chưa được dùng**.
- Lớp 1 và 2 ở trên đã làm: lần tìm không có tương tác nào (kể cả "bỏ qua") bị bỏ; lấy ứng viên tới hạng tương tác
  thấp nhất + `viewed-depth-margin`; ứng viên `isExploration` có `s = exploration-sample-weight`.
- Đặc trưng thiếu trong log (null) = `confidence-prior` 0,5, như khi tính a. Chỉ những ứng viên được log giữ đủ đặc
  trưng (`log-candidate-limit`) mới có mặt.

## 12.6. Phía b: hồi quy Bayes trên quyết định nhận / từ chối

> 💡 Đáp án là quyết định thật trong bảng đơn: nhận = 1, từ chối = 0. Thêm vài loại mẫu phụ với trọng số nhỏ hơn, ví dụ gia sư **chủ động mời** một học sinh cũng là dấu hiệu sẵn lòng dạy kiểu học sinh đó; đơn chờ quá 14 ngày coi như từ chối ngầm. Vì các con số thay đổi theo thời gian, lúc tạo đơn hệ thống chụp lại (**snapshot**) các thành phần b tại thời điểm đó. Phía b dùng thẳng μ, không thử ngẫu nhiên.

Nhãn phía b là **quyết định thật của phía kia**, có ngay trong bảng đơn và không phụ thuộc vị trí hiển
thị:

| Mô hình | Nguồn | Nhãn |
|---|---|---|
| `B_S2T` | `student_tutor_requests` | `ACCEPTED` = 1, `REJECTED` = 0; `PENDING`, huỷ, hết hạn bị bỏ |
| `B_T2S` | `tutor_student_requests` | như trên |

**Mẫu bổ sung** (có trọng số mẫu `s` riêng):

| Mẫu | Cho mô hình | Nhãn | s |
|---|---|---|---|
| Quyết định nhận / từ chối thật | `B_S2T`, `B_T2S` | 1 / 0 | 1 |
| Nhận trong 24 giờ | như trên | 1 | 1,5 |
| **Gia sư chủ động gửi đề nghị** cho một tin học (`tutor_student_requests`) | `B_S2T` — gia sư sẵn lòng dạy kiểu học sinh này | 1 | 0,5 |
| **Học sinh chủ động gửi đơn** vào một tin dạy (`student_tutor_requests`) | `B_T2S` — học sinh sẵn lòng học với kiểu gia sư này | 1 | 0,5 |
| Từ chối ngầm: đơn `PENDING` quá 14 ngày, hoặc bị hệ thống chuyển `CANCELLED` vì tin đã ghép với người khác | `B_S2T`, `B_T2S` | 0 | 0,5 |

Đơn do chính người gửi huỷ (`CANCELLED` không phải do hệ thống) bị bỏ — đó không phải quyết định của phía
kia. `ApplicationStatus` hiện chỉ có `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` (không có hết hạn), nên
"từ chối ngầm" được suy ra khi dựng dữ liệu học, không thêm trạng thái mới.

**Ảnh chụp đặc trưng (snapshot):** `b_hist`, thành phần fit và mức dồn đơn thay đổi theo thời gian, nên
phải lưu **giá trị tại thời điểm đơn được tạo**. Khi một đơn / đề nghị được tạo (dù có đi qua tính năng
đề xuất hay không), hệ thống tính các thành phần b cho cặp đó và ghi vào
`recommendation_decision_snapshots` (12.8) trong cùng transaction. Một đơn tạo **hai** ảnh chụp: một cho mô
hình chờ phía kia quyết định (ví dụ đơn của học sinh → `B_S2T`), một mẫu dương "chủ động" cho mô hình chiều
ngược lại (`B_T2S`). Nhờ vậy phía b có dữ liệu từ **mọi** đơn trên nền tảng, nhiều hơn phía a.

Phía b dùng **trung bình hậu nghiệm μ**, không rút mẫu: phía b không cần khám phá vì nhãn không phụ thuộc
thứ tự hiển thị, và thử nghiệm trên b sẽ ảnh hưởng tới phía bên kia (gửi nhầm đơn cho gia sư khó nhận).

`b_cap` vẫn nhân sau cùng, không học (12.2).

**Ghi chú triển khai (20/10/2026) — dữ liệu học phía b.** `LearningDataRepository.decisionSamples(model, from, to)`
nối ảnh chụp với bảng đơn; mẫu được tính vào khoảng thời gian theo **thời điểm nhãn được biết**: lúc trả lời
(`responded_at`, không có thì `updated_at`), mốc tạo + 14 ngày với từ chối ngầm, lúc tạo đơn với mẫu chủ động. Đơn bị
xoá bị bỏ.

> 💡 Phát hiện khi code: hiện khi một tin đã ghép với người khác, các đơn đang chờ vào tin đó **không** bị hệ thống chuyển `CANCELLED` mà vẫn `PENDING` (hệ thống chỉ huỷ đề nghị khi học sinh tự huỷ tin học). Vì vậy loại "bị hệ thống huỷ vì tin đã ghép" gần như không xuất hiện; các đơn đó vẫn thành từ chối ngầm sau 14 ngày chờ, nên không mất dữ liệu.

Quy tắc đã cài cho `CANCELLED`: tính là từ chối ngầm khi tin đăng của đơn đang `MATCHED`; huỷ khi tin còn mở (người
gửi tự rút lại, hoặc chủ tin huỷ tin) không cho nhãn.
**Ghi chú triển khai (20/10/2026).**

> 💡 Ảnh chụp được tính **trước** khi lưu đơn (nên đơn vừa gửi không tự tính vào số đơn đang chờ) và ghi **sau** khi lưu, đều trong cùng transaction tạo đơn. Mỗi phần chạy sau một "điểm lưu" (savepoint) của PostgreSQL: nếu lỗi thì quay lại điểm lưu, ghi log cảnh báo, đơn vẫn được tạo bình thường. Phía "chủ động" khi người gửi không có tin đăng ở chiều kia được dựng từ chính đơn và hồ sơ của họ.

- `DecisionSnapshotRecorder` (gọi trong `StudentTutorRequestService.createForStudent` và
  `TutorStudentRequestService.create`): `prepare…` trước `saveAndFlush`, `record` sau. Lỗi ở bất kỳ bước nào → quay
  về savepoint, log cảnh báo, không có ảnh chụp, đơn vẫn được tạo (quyết định của người dùng: savepoint trong cùng
  transaction, không ghi sau commit). Ghi bằng SQL thuần, không qua Hibernate, để một lần ghi lỗi không để lại thực
  thể hỏng trong phiên. Giới hạn: nếu chính **truy vấn đọc qua JPA** (dựng tiêu chí) gặp lỗi SQL, Spring vẫn đánh
  dấu transaction phải rollback — trường hợp này coi như lỗi của database, không xử lý riêng.
- **Đơn của học sinh** vào tin dạy T: `B_S2T` / DECISION dựng tiêu chí của học sinh như một lần tìm (lớp, giá
  `proposedPrice` làm ngân sách, hình thức lấy từ đơn; phần còn lại từ hồ sơ, 05 mục 5.5) và tin T như ứng viên.
  `B_T2S` / INITIATIVE: phía gia sư lấy từ tin T (`useTeachingRequestId`), học sinh được dựng thành "ứng viên tin học"
  từ chính tiêu chí đó và lịch sử trả lời đề nghị của học sinh (quyết định của người dùng: dựng từ đơn + hồ sơ).
- **Đề nghị của gia sư** vào tin học S: `B_T2S` / DECISION dựng tiêu chí gia sư từ đề nghị (lớp, giá, hình thức) và
  hồ sơ, tin S là ứng viên. `B_S2T` / INITIATIVE: phía học sinh lấy từ tin S, gia sư được dựng thành "ứng viên tin
  dạy" từ tiêu chí của gia sư và lịch sử trả lời đơn của gia sư.
- `features` = `{"hist", các phần fit theo tên của mô hình, "cap", "pending", "reachedOut", "p0"}`; phần fit là giá
  trị b thực sự dùng (phần thiếu đã được thay theo 12.2). `reachedOut` = phía kia đã chủ động trước (với mẫu
  INITIATIVE luôn true). `weights_version` để trống cho tới khi có bản học được.

## 12.7. Vòng đời và cơ chế an toàn

> 💡 Mỗi mô hình có ba chế độ và điều kiện chuyển chế độ, job chạy mỗi đêm, và lưới an toàn để quay về trọng số nhóm đặt bất cứ lúc nào.

### Ba chế độ cho mỗi mô hình

> 💡 `OFF`: không học, dùng prior. `SHADOW` (**chạy ngầm**, mặc định): vẫn học và ghi lại mỗi đêm nhưng người dùng vẫn được phục vụ bằng prior. `ACTIVE`: người dùng được phục vụ bằng bản học được.

| Chế độ | Phục vụ người dùng bằng | Học | Dùng khi |
|---|---|---|---|
| `OFF` | Prior (`RecommendationSettings`) | Không | Mặc định ban đầu; tắt khẩn cấp |
| `SHADOW` | Prior | Có — tính bản mới mỗi đêm, ghi log, so sánh | Đang tích luỹ dữ liệu |
| `ACTIVE` | Bản học được (phía a: rút mẫu Thompson) | Có | Đã qua điều kiện kích hoạt |

### Điều kiện kích hoạt (từ `SHADOW` sang `ACTIVE`)

> 💡 Chỉ chuyển sang dùng thật khi đủ dữ liệu (phía a ≥ 2.000 quan sát, phía b ≥ 300 quyết định) **và** bản học được không tệ hơn prior trên dữ liệu 7 ngày gần nhất chưa dùng để học. **Log-loss** là chỉ số đo dự đoán xác suất sai tới đâu (càng nhỏ càng tốt).

1. Đủ dữ liệu: phía a ≥ `learning.min-samples-a` (2 000 quan sát, từ ≥ 300 lần tìm có tương tác); phía b ≥
   `learning.min-samples-b` (300 quyết định).
2. **Đánh giá lại trên dữ liệu gần nhất chưa dùng để học** (7 ngày cuối): bản học được phải không tệ hơn
   prior — phía a so NDCG@10 theo phần thưởng 12.4 trên các lần tìm có tương tác; phía b so log-loss của
   dự đoán nhận. Chênh lệch được ghi vào `evaluation`.
3. `learning.auto-activate = true` thì job tự kích hoạt; `false` thì chờ admin bấm (12.9).

### Mỗi đêm — `WeightLearningJob` (20:30 UTC = 03:30 giờ Việt Nam, sau `ExposureStatsJob` lúc 20:00 UTC)

> 💡 Bảy bước của job đêm: đọc bản cũ → đọc dữ liệu mới → cập nhật A, c → tính trọng số mới và đánh giá → lưu thành **phiên bản mới** (không ghi đè bản cũ) → đủ điều kiện thì kích hoạt → báo cho `WeightProvider` nạp lại.

```
Với mỗi mô hình có chế độ ≠ OFF:
  1. Lấy bản mới nhất (hoặc prior nếu chưa có) — gồm A, c, mốc trained_until
  2. Đọc dữ liệu mới:
       phía a: log có created_at trong (trained_until, now − 7 ngày]   — chờ đủ 7 ngày để phần thưởng "chốt"
       phía b: quyết định có thời điểm trong (trained_until, now]
  3. Dựng (x, r, s), cập nhật A, c (có quên dần nếu bật)
  4. μ → project → trọng số mới; đánh giá như điều kiện 2
  5. Chèn MỘT DÒNG MỚI (version + 1) — không ghi đè bản cũ
  6. Nếu đủ điều kiện kích hoạt: đánh dấu is_active cho bản mới, bỏ đánh dấu bản cũ
  7. Phát LearnedWeightsUpdatedEvent → WeightProvider nạp lại
```

### Lưới an toàn

> 💡 Bốn lớp bảo vệ: kẹp và giới hạn bước; quay lui về phiên bản cũ hoặc tắt hẳn; bản mới kém hơn thì không được dùng; mọi lần tìm đều ghi phiên bản trọng số đã dùng để truy lại được.

- Kẹp quanh prior và giới hạn bước mỗi đêm (12.3).
- Quay lui: kích hoạt lại một version cũ, hoặc chuyển chế độ `OFF` để về prior ngay.
- Bản mới tệ hơn prior trên dữ liệu kiểm tra → **không** kích hoạt, bản đang dùng giữ nguyên.
- Mọi lần tìm ghi version trọng số đã dùng → truy được kết quả nào xếp bằng bộ trọng số nào.

### Ghi chú triển khai (20/10/2026)

> 💡 Job đêm đã có: mỗi mô hình (trừ `OFF`) đọc dữ liệu mới từ mốc của bản trước, giữ riêng 7 ngày mới nhất để so với prior, học phần còn lại rồi lưu thành phiên bản mới. Đủ dữ liệu và không tệ hơn prior thì đánh dấu "đang dùng"; nhưng người dùng chỉ thật sự nhận bản học được khi chế độ cấu hình là `ACTIVE`.

- `WeightLearningJob` (lịch `learning.job-cron`) gọi `WeightLearningService.learnAll(now)`; mỗi mô hình một
  transaction riêng, lỗi của một mô hình không chặn mô hình khác (log `WARN`).
- **Khoảng dữ liệu:** "có thể dùng tới" = `now − reward-window-days` với phía a, `now` với phía b. Học trên
  `(trained_until, có thể dùng tới − evaluation-days]`; **giữ riêng** `evaluation-days` (7) ngày cuối để đánh giá —
  đúng nghĩa "dữ liệu gần nhất chưa dùng để học"; đêm sau các ngày đó được học. `trained_until` của bản mới = mốc
  cuối của khoảng học. Mô hình chưa có bản nào bắt đầu từ 01/01/2000.
- **Không có quan sát mới → không tạo phiên bản** (chỉ ghi log), để không sinh version rỗng mỗi đêm.
- "Bản trước" của bước giới hạn = trọng số của phiên bản mới nhất (hoặc prior).
- **Đánh giá:** phía a — NDCG@10 trung bình trên các lần tìm giữ riêng có ít nhất một thưởng dương, xếp theo Σ w·x,
  điểm thưởng âm tính là 0; phía b — log-loss có trọng số mẫu của Σ w·x (b trước `b_cap`), kẹp xác suất trong
  [10⁻⁶; 1 − 10⁻⁶]. "Không tệ hơn" cần ít nhất một mẫu giữ riêng.
- **Kích hoạt:** `auto-activate` và đủ dữ liệu (phía a: `min-samples-a` quan sát **và** `min-searches-a` lần tìm có
  tương tác, tính cộng dồn; phía b: `min-samples-b`) và không tệ hơn prior → bỏ đánh dấu bản cũ rồi chèn bản mới với
  `is_active = true` (chỉ mục duy nhất một bản active mỗi mô hình). Kích hoạt **không đổi chế độ**: ở `SHADOW`
  người dùng vẫn nhận prior (đúng "SHADOW không đổi trọng số phục vụ"). Có bản được kích hoạt → phát
  `LearnedWeightsUpdatedEvent` sau khi các transaction đã commit.
- `evaluation` lưu `metric`, `learned`, `prior`, `n` (số mẫu giữ riêng), `newSamples`, `searches` (cộng dồn),
  `enoughData`, `clamped` (các đặc trưng có μ nằm ngoài biên kẹp).

## 12.8. Lưu trữ — migration V34

> 💡 Hai bảng mới: một bảng lưu các phiên bản trọng số (kèm A, c và cờ đang dùng), một bảng lưu ảnh chụp thành phần b lúc tạo đơn; cộng các trường thêm vào nhật ký tìm kiếm.

```sql
CREATE TABLE recommendation_learned_weights (
    id              BIGSERIAL PRIMARY KEY,
    model_key       VARCHAR(20)  NOT NULL,      -- A_S2T, A_T2S, B_S2T, B_T2S
    version         INT          NOT NULL,
    feature_names   JSONB        NOT NULL,      -- ["intercept","sem","need",...] — thứ tự của A, c
    weights         JSONB        NOT NULL,      -- trọng số đã kẹp + chuẩn hoá, dùng để xếp hạng
    posterior_mean  JSONB        NOT NULL,      -- μ chưa kẹp (để phân tích)
    stats_a         JSONB        NOT NULL,      -- ma trận A
    stats_c         JSONB        NOT NULL,      -- vector c
    prior_weights   JSONB        NOT NULL,      -- prior tại thời điểm tạo (để biết đã dịch bao xa)
    sample_count    INT          NOT NULL,      -- tổng số quan sát đã học
    trained_until   TIMESTAMP    NOT NULL,
    evaluation      JSONB,                      -- {"metric":"ndcg10","learned":0.61,"prior":0.58,"n":412}
    is_active       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learned_weights_version UNIQUE (model_key, version),
    CONSTRAINT ck_learned_weights_model CHECK (model_key IN ('A_S2T','A_T2S','B_S2T','B_T2S'))
);
CREATE UNIQUE INDEX uq_learned_weights_active
    ON recommendation_learned_weights (model_key) WHERE is_active;

CREATE TABLE recommendation_decision_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    model_key       VARCHAR(20)  NOT NULL,      -- B_S2T, B_T2S
    sample_kind     VARCHAR(20)  NOT NULL,      -- DECISION (chờ phía kia quyết định), INITIATIVE (mẫu dương chủ động)
    request_type    VARCHAR(30)  NOT NULL,      -- STUDENT_TUTOR_REQUEST, TUTOR_STUDENT_REQUEST
    request_id      UUID         NOT NULL,
    features        JSONB        NOT NULL,      -- {"hist":0.70,"fitSegment":1,"fitSched":0.5,"fitPrice":1,"cap":0.4,"pending":3}
    weights_version JSONB,                      -- version các mô hình lúc tạo (nếu có)
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_decision_snapshot UNIQUE (model_key, sample_kind, request_id),
    CONSTRAINT ck_decision_snapshot_model CHECK (model_key IN ('B_S2T','B_T2S')),
    CONSTRAINT ck_decision_snapshot_kind CHECK (sample_kind IN ('DECISION','INITIATIVE'))
);
```

Chế độ của từng mô hình (`OFF` / `SHADOW` / `ACTIVE`) nằm trong cấu hình (`learning.models.<key>.mode`) để
đổi được khi khởi động; admin có thể ghi đè lúc chạy (12.9). **Quyết định (20/10/2026, người dùng chọn):** dự án
không có bảng cấu hình hệ thống, nên giá trị ghi đè lưu trong bảng mới `recommendation_learning_modes` (migration
`V35`: `model_key` khoá chính, `mode`, `updated_by`, `updated_at`). Mô hình không có dòng nào dùng chế độ của cấu hình.

**Bổ sung vào log tìm kiếm** (`recommendation_logs`):
- `query_context.weights`: `{"A": {"model":"A_S2T","version":14,"mode":"ACTIVE","sampled":true,
  "values":{…}}, "B": {…}}` — trọng số **thực sự đã dùng** (mẫu Thompson, không phải μ).
- Mỗi ứng viên trong `returned_candidate_ids`: thêm các thành phần fit (`fitSegment`, `fitSched`,
  `fitPrice`) bên cạnh `willingness.hist/fit/cap` đang có — để phân tích và tái hiện điểm.
- `log-candidate-limit` giữ 50 khi chạy thật; khi chạy thí nghiệm mô phỏng nâng lên 200.

**Ghi chú triển khai (20/10/2026):** đã tạo `V34__add_recommendation_weight_learning.sql` đúng hai bảng trên, thêm
ràng buộc `ck_decision_snapshot_request_type` và chỉ mục `(request_type, request_id)` để đọc ảnh chụp theo đơn khi
dựng dữ liệu học. Entity `RecommendationLearnedWeights`, `RecommendationDecisionSnapshot` và repository tương ứng.
Log tìm kiếm đã có `query_context.weights` (`version = null` khi phục vụ prior) và `fitSegment`, `fitSched`,
`fitPrice` (chiều học sinh tìm) / `fitQuality`, `fitStyle`, `fitPrice` (chiều gia sư tìm) trong `willingness` của
từng ứng viên.

## 12.9. Thành phần và luồng

> 💡 Các lớp code sẽ có: `WeightProvider` giữ trọng số hiện hành trong bộ nhớ (không truy vấn database mỗi lần tìm); `LinearBayesModel` là phần toán thuần; `WeightLearningJob` là job đêm; `DecisionSnapshotRecorder` ghi ảnh chụp khi tạo đơn. Khi L1 tắt, kết quả phải giống hệt hiện tại (có test kiểm tra).

```
RecommendationSettings ──(prior, giới hạn, chế độ)──┐
                                                      ▼
recommendation_learned_weights ──(bản is_active)──► WeightProvider ──► FeatureCalculator / TutorSideRanker
        ▲                                             ▲  (nạp lúc khởi động,   (a, a′)
        │                                             │   nạp lại khi có event) WillingnessCalculator (b, b′)
        │                                             │
WeightLearningJob ──LearnedWeightsUpdatedEvent────────┘
        ▲
        │ dữ liệu
recommendation_logs + recommendation_click_logs (phía a)
recommendation_decision_snapshots + bảng đơn (phía b) ◄── DecisionSnapshotRecorder (khi đơn được tạo)
```

| Thành phần | Việc làm |
|---|---|
| `WeightProvider` | Giữ bản trọng số hiện hành của 4 mô hình trong bộ nhớ (`AtomicReference` tới một đối tượng bất biến). **Nạp lúc khởi động** và **nạp lại khi nhận `LearnedWeightsUpdatedEvent`**; thêm **nạp lại định kỳ 10 phút** làm lưới an toàn (`learning.reload-interval-ms`, thêm 21/10/2026: event chỉ tới tiến trình phát ra nó, nên khi chạy nhiều backend hoặc admin đổi chế độ ở tiến trình khác, các tiến trình còn lại vẫn cập nhật trong tối đa 10 phút). Không truy vấn database khi tìm. Phía a: trả mẫu Thompson theo hạt giống (người dùng, ngày) hoặc μ; phía b: trả μ. Chế độ `OFF`, chưa có bản active, hay lỗi đọc → trả prior |
| `LinearBayesModel` | Lớp toán thuần (không Spring): cập nhật A, c; tính μ; rút mẫu (Cholesky); `project`. Có test đối chiếu với bản Python dùng trong thí nghiệm |
| `WeightLearningJob` | Job đêm ở 12.7 |
| `LearningDataRepository` | SQL dựng dữ liệu học: nối log với click log (7 ngày) cho phía a; nối snapshot với bảng đơn cho phía b |
| `DecisionSnapshotRecorder` | Gọi trong service tạo đơn / đề nghị (hoặc listener của sự kiện tạo), ghi snapshot |
| Admin API (tuỳ chọn) | `GET /api/v1/admin/recommendations/weights` (prior, bản active, lịch sử version, đánh giá); `POST …/weights/{model}/activate/{version}`; `PUT …/weights/{model}/mode` — đã làm 20/10/2026 (`AdminRecommendationWeightController`, `AdminRecommendationWeightService`), chỉ ADMIN |

Các calculator **không đọc trọng số trực tiếp từ `RecommendationSettings` nữa** mà hỏi `WeightProvider`.
Khi L1 tắt, `WeightProvider` trả đúng giá trị của `RecommendationSettings` → kết quả không đổi so với
hiện tại (có test khẳng định điều này).

**Trạng thái (20/10/2026):** `WeightProvider` đã có và các calculator (`FeatureCalculator`, `TutorSideRanker`,
`WillingnessCalculator`) đã hỏi nó. Bước 21: nạp bản `is_active` của từng mô hình lúc khởi động
(`ApplicationReadyEvent`) và khi có `LearnedWeightsUpdatedEvent`, giữ trong `AtomicReference` một `Map` bất biến;
chế độ `OFF` / `SHADOW` / `ACTIVE` theo `learning.models.<key>.mode` (`learning.enabled = false` → mọi mô hình về
`OFF`); lỗi khi nạp → mọi mô hình phục vụ prior tới lần nạp sau. Bảng `recommendation_learned_weights` lưu `weights`
dạng đối tượng `{tên đặc trưng: giá trị}` (không có hệ số chặn), `stats_a` mảng hai chiều, `stats_c` mảng (có hệ số
chặn ở đầu). Trọng số của một mô hình là `ModelWeights` (bất biến)
theo thứ tự đặc trưng của `WeightModel`: `A_S2T` sem, need, price, style, sched, area, segment; `A_T2S` sem, price,
need, segment, sched, area; `B_S2T` hist, fitSegment, fitSched, fitPrice; `B_T2S` hist, fitQuality, fitStyle,
fitPrice. a và a′ tính ở dạng `Σ w_k·x_k` (đặc trưng thiếu = `confidence-prior`), b và b′ ở dạng dàn phẳng;
`willingness.fit` trong log vẫn là `b_fit` (trung bình có trọng số các phần fit). Chế độ đang áp dụng của một mô hình
= `OFF` nếu tắt L1, nếu không thì chế độ admin đặt (nạp cùng lúc với các phiên bản), nếu không thì cấu hình; job đêm
cũng bỏ qua mô hình theo chế độ này. Kích hoạt một phiên bản hoặc đổi chế độ qua API quản trị: ghi trong transaction
riêng, rồi phát `LearnedWeightsUpdatedEvent` để `WeightProvider` nạp lại. Nạp bản học được, chế độ và mẫu
Thompson làm ở bước sau.

## 12.10. Tham số `app.recommendation.learning.*`

> 📌 **Đã làm 27/10/2026:** `prior-strength` 200 → **50**, `exploration-scale` 1,0 → **0,5**, `forgetting` 1,0 →
> **0,99**; `max-step` giữ 0,05. Trên mô phỏng `crowded` có truy vấn đa dạng, L1 với mặc định mới có regret thấp hơn
> trọng số cố định 21 % (10/10 hạt giống), cả khi sở thích đổi giữa chừng; khám phá không hơn Greedy (tốn ~2 %
> regret) nhưng vẫn giữ ở mức thấp. Lý do và bằng chứng: [13, mục 13.7](./13-tinh-chinh-sau-danh-gia.md).

> 💡 Bảng mọi tham số của cơ chế học, mỗi dòng có ý nghĩa ngắn. Các tham số quan trọng nhất: κ (prior đáng bao nhiêu quan sát), ρ (mức thử), khoảng kẹp 0,5–2, bước tối đa 0,05, ngưỡng dữ liệu để kích hoạt.

| Tham số | Giá trị khởi đầu | Ý nghĩa |
|---|---|---|
| `enabled` | `true` | Tắt toàn bộ L1 (mọi mô hình về prior) |
| `models.<key>.mode` | `SHADOW` | `OFF` / `SHADOW` / `ACTIVE` cho từng mô hình |
| `prior-strength` (κ) | 50 | Prior đáng giá bao nhiêu quan sát (200 trước 27/10/2026) |
| `intercept-prior-strength` (κ₀) | 1 | Độ mạnh prior của hệ số chặn (thêm 21/10/2026) |
| `reload-interval-ms` | 600 000 | Nạp lại trọng số định kỳ, lưới an toàn cho event (thêm 21/10/2026) |
| `noise` (ν) | 0,3 | Độ nhiễu giả định của phản hồi |
| `exploration-scale` (ρ) | 0,5 | Mức khám phá Thompson phía a; 0 = tham lam (1,0 trước 27/10/2026) |
| `min-ratio` / `max-ratio` | 0,5 / 2,0 | Biên kẹp quanh prior |
| `max-step` | 0,05 | Thay đổi tối đa của mỗi trọng số trong một lần cập nhật (một đêm) |
| `reward-window-days` | 7 | Thời gian chờ phần thưởng phía a "chốt" |
| `rewards` view / favorite / contact / request / dismissed | 0,3 / 0,6 / 0,8 / 1,0 / −0,2 | Thang thưởng phía a (12.4) |
| `rewards.repeat-view` / `unfavorite` / `alert-click` / `bounce` | bật / bật / tắt / tắt | Tín hiệu tuỳ chọn |
| `b-samples.fast-accept-hours` / `fast-accept-weight` | 24 / 1,5 | Mẫu nhận nhanh |
| `b-samples.initiative-weight` | 0,5 | Mẫu dương chủ động |
| `b-samples.implicit-reject-days` / `implicit-reject-weight` | 14 / 0,5 | Từ chối ngầm |
| `viewed-depth-margin` | 2 | Lấy ứng viên tới vị trí tương tác thấp nhất + 2 |
| `exploration-sample-weight` | 2 | Trọng số mẫu của ứng viên ở slot khám phá |
| `ipw.enabled` / `ipw.max-weight` | `false` / 10 | Trọng số nghịch đảo xác suất được nhìn |
| `forgetting` (γ) | 0,99 | Hệ số quên, áp một lần trước mỗi lần cập nhật (mỗi đêm); ~70 đêm thì dữ liệu cũ còn nửa sức nặng (1,0 trước 27/10/2026) |
| `min-samples-a` / `min-samples-b` | 2 000 / 300 | Điều kiện kích hoạt |
| `auto-activate` | `true` | Tự kích hoạt khi đủ điều kiện |
| `job-cron` | `0 30 20 * * *` (UTC) | Lịch `WeightLearningJob` |
| `evaluation-days` | 7 | Số ngày mới nhất giữ riêng để so bản mới với prior (thêm khi code, 20/10/2026) |
| `min-searches-a` | 300 | Số lần tìm có tương tác tối thiểu (cộng dồn) để kích hoạt phía a — điều kiện "từ ≥ 300 lần tìm" của 12.7 (thêm khi code) |
| `clamp-warning-nights` | 3 | Một trọng số bị kẹp chừng này đêm liền thì log `WARN` (10, mục 10.4; thêm khi code) |

## 12.11. Chứng minh trong đồ án

> 💡 Chưa có người dùng thật nên chỉ chứng minh **cơ chế** trên dữ liệu mô phỏng (09 mục 9.9). Có câu trình bày mẫu, những điều **không được tuyên bố**, và bảng câu hỏi hội đồng có thể hỏi kèm câu trả lời ngắn.

Chưa có người dùng thật, nên đồ án chứng minh **cơ chế học** trên bộ mô phỏng của 09 — nơi "trọng số thật"
của người dùng ảo được biết trước nhưng hệ thống không nhìn thấy. Chi tiết thí nghiệm ở 09, mục 9.9.

**Câu trình bày đề xuất:**

> Trọng số do nhóm thiết lập đóng vai trò prior cho giai đoạn khởi đầu lạnh. Khi vận hành thật, hệ thống
> dùng contextual bandit (Linear Thompson Sampling) cho độ phù hợp phía người tìm và hồi quy Bayes trực
> tuyến cho khả năng phía kia đồng ý, tự tinh chỉnh trọng số từ phản hồi. Trên bộ mô phỏng có đáp án ẩn,
> trọng số học được tiến về trọng số thật và regret thấp hơn trọng số cố định khoảng 21 %, cả khi sở thích
> thay đổi giữa chừng. **Hệ thống tự sửa được sai lệch vừa phải nằm trong biên an toàn quanh trọng số nhóm
> đặt; một prior sai ngược hẳn thì chỉ sửa được một phần** — đó là đánh đổi có chủ đích giữa an toàn và khả
> năng tự sửa.

**Không tuyên bố:** mô hình "đã học được" sở thích của người dùng thật; số lượng dữ liệu cần thiết ngoài
đời thật (chỉ nêu con số đọc từ mô phỏng, kèm điều kiện); hệ thống sửa được mọi prior sai; khám phá có lợi
(trong mô phỏng khám phá không hơn Greedy và tốn ~2 % regret — 13 mục 13.7).

**Câu hỏi hội đồng dự kiến:**

| Câu hỏi | Trả lời ngắn |
|---|---|
| Vì sao không dùng deep RL? | Bài toán một bước, không có chuyển trạng thái; dữ liệu ít. Contextual bandit là dạng RL phù hợp, có đảm bảo lý thuyết về regret (Agrawal & Goyal, 2013) |
| Vì sao Thompson sampling, không phải UCB? | Dễ cập nhật theo lô (job đêm), chịu được phản hồi trễ, thực nghiệm tốt (Chapelle & Li, 2011); nhất quán với slot khám phá R6 |
| Trọng số ban đầu chọn sai thì sao? | (sửa 27/10/2026) Thí nghiệm "prior sai" trong 9.9: sai vừa (chia đều) thì tự sửa phần lớn (regret −34 % so với giữ nguyên prior đó, sai số trọng số 0,54 → 0,23); sai ngược hẳn thì chỉ sửa được một phần (−29 %), vì biên [0,5; 2] × prior không cho đi đủ xa. Đó là đánh đổi có chủ đích giữa an toàn và khả năng tự sửa |
| Khám phá không có lợi trong mô phỏng, sao vẫn giữ? | Trong mô phỏng khám phá tốn ~2 % regret so với Greedy. Môi trường thật có bất định mô phỏng không có (người dùng mới, môn mới, tin đăng mới); ρ = 0 thì L1 chỉ còn là hồi quy Bayes, không còn là contextual bandit (13.7) |
| Thiên lệch vị trí, vòng lặp phản hồi? | Ba lớp ở 12.5; Thompson sampling chủ động thử nghiệm nên không chỉ học từ những gì nó đã đưa lên đầu |
| Sao không học tất cả tham số? | Bảng "giữ cố định" ở 12.2 — một số là chính sách thị trường hoặc định nghĩa phần thưởng, không nên học |
| Bao giờ thì đủ dữ liệu? | Đọc từ đường hội tụ trong mô phỏng; khi chạy thật có điều kiện kích hoạt và chế độ `SHADOW` |

## 12.12. Vì sao chọn mô hình này

> 💡 Sáu tiêu chí chọn (ít dữ liệu, dùng được trọng số nhóm đặt, giữ cấu trúc công thức, giải thích được, chạy rẻ trong Java, chứng minh được trên mô phỏng) và bảng so sánh với các phương án khác — ε-greedy (thỉnh thoảng thử ngẫu nhiên hoàn toàn), LinUCB, A/B test, deep RL… — kèm lý do không chọn.

Sáu tiêu chí xuất phát từ hoàn cảnh đồ án: (1) ít hoặc chưa có dữ liệu; (2) dùng được trọng số tự đặt làm
điểm xuất phát; (3) giữ nguyên cấu trúc `R = 2ab/(a+b)…`; (4) giải thích được từng trọng số; (5) chạy trong
Java, không GPU, cập nhật rẻ mỗi đêm; (6) chứng minh được trên mô phỏng.

| Phương án | Ưu | Vì sao không chọn |
|---|---|---|
| Giữ cố định, hiệu chỉnh offline (tìm lưới, AHP) | Đơn giản nhất | Không tự học khi chạy thật |
| ε-greedy | Rất dễ code | Thử mù, không dùng thông tin "chỗ nào chưa chắc"; làm xấu trải nghiệm đều đặn |
| Multi-armed bandit trên vài bộ trọng số định sẵn | Dễ giải thích | Chỉ chọn được trong các bộ đã liệt kê, không tìm ra giá trị ở giữa |
| LinUCB (Li và cộng sự, 2010) | Cùng họ, lý thuyết tốt | Tất định: cập nhật theo lô mỗi đêm thì cả ngày mọi người cùng một bộ trọng số, không có thử nghiệm đa dạng; Thompson sampling chịu cập nhật theo lô và phản hồi trễ tốt hơn (Chapelle & Li, 2011) |
| Dueling bandit / interleaving (Yue & Joachims, 2009) | Học trọng số xếp hạng tuyến tính từ click, chống thiên lệch vị trí tốt | Phải trộn hai danh sách khi hiển thị; hội tụ chậm; phức tạp hơn — **phương án thay thế đáng nêu** |
| Hồi quy logistic huấn luyện lại định kỳ | Chuẩn mực | Không khám phá → dễ thành vòng lặp tự củng cố ở phía a. Dùng ý tưởng này cho phía b, nơi không cần khám phá |
| Tối ưu hoá Bayes / A/B test từng bộ trọng số | Thực tế trong công nghiệp | Mỗi lần thử cần một khoảng thời gian chạy thật — rất chậm với lượng người dùng nhỏ |
| Learning-to-rank (LightGBM LambdaMART), mạng nơ-ron | Mạnh nhất khi nhiều dữ liệu | Cần hàng nghìn phiên có nhãn, khó giải thích, không khám phá — hướng phát triển, huấn luyện trên log của L1 |
| Deep RL (DQN, policy gradient) | — | Bài toán một bước, không có chuỗi trạng thái; cần dữ liệu khổng lồ; không giải thích được |

**Vì sao tách a và b thành hai mô hình** thay vì học thẳng điểm cuối: hai phía có tín hiệu khác nhau; gộp lại
thì không biết điểm thấp là do người tìm không thích hay phía kia từ chối. Tách ra giữ đúng tinh thần hai
chiều của RECON.

**Vì sao phía b dùng hồi quy tuyến tính Bayes, không phải logistic** dù nhãn là 0/1: tuyến tính Bayes có
công thức cập nhật đóng (lưu bằng A, c) và giữ dạng tổng có trọng số như công thức b hiện tại. Logistic tự
nhiên hơn với nhãn nhị phân nhưng đổi cấu trúc thành `σ(θ·z)` và cần xấp xỉ (Laplace). Đây là đánh đổi có chủ
đích; có thể so sánh hai cách trong thí nghiệm mô phỏng (09, mục 9.9) nếu còn thời gian.

## 12.13. Giới hạn và mở rộng

> 💡 Những gì mô hình chưa làm được (trọng số chung cho mọi người, mô hình tuyến tính, phản hồi bấm chưa phản ánh hết "ghép đôi tốt lâu dài") và hướng mở rộng.

**Giới hạn:**
- Mô hình tuyến tính, trọng số **chung cho mọi người dùng** của một chiều; cá nhân hoá vẫn do H1 và B5 đảm
  nhận.
- Thang thưởng và mô hình hành vi trong mô phỏng do nhóm đặt.
- Phản hồi click là đại diện chưa hoàn hảo cho "ghép đôi tốt lâu dài".
- Phía b chỉ có dữ liệu từ khi bắt đầu ghi snapshot.

**Mở rộng:**
- Kết quả dài hạn (hợp đồng đủ buổi, gia hạn, đánh giá; huỷ, khiếu nại) làm phần thưởng phụ, cửa sổ 60 ngày.
- Sự kiện thời gian ở trang chi tiết từ frontend.
- Học thêm `w_hist`, tỉ lệ rerank / cosine (đưa `f_rerank`, `f_cos` thành hai đặc trưng riêng).
- Trọng số theo nhóm (ví dụ theo cấp học) — một mô hình cho mỗi nhóm, prior là mô hình chung.
- Chuyển sang learning-to-rank (LightGBM) khi đủ dữ liệu (10, mục 10.5), dùng chính log L1 làm dữ liệu
  huấn luyện — log đã ghi trọng số đã dùng nên tính được xác suất hiển thị cho đánh giá ngoại tuyến.
