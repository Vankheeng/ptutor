# CONTRIBUTING.md — Quy trình làm việc với Git

## 1. Sơ đồ nhánh

```
main  (production, protected, chỉ nhận PR từ dev khi release)
 └── dev  (integration, protected, nơi các feature/fix merge vào)
      ├── feature/<mo-ta>
      ├── fix/<mo-ta>
      └── hotfix/<mo-ta>   (chỉ dùng khi cần vá gấp trên main)
```

- `main`: luôn là code đang chạy production. Không push trực tiếp, không PR thẳng từ feature vào main.
- `dev`: nhánh tích hợp, nơi các nhánh nhỏ merge vào hàng ngày.
- `feature/*`, `fix/*`: nhánh làm việc, tạo từ `dev`, sống ngắn hạn (vài ngày), xoá sau khi merge.

## 2. Trước khi tạo nhánh mới

```bash
git checkout dev
git pull origin dev
git checkout -b feat/user-crud
```

Luôn cập nhật `dev` trước khi tạo nhánh mới để tránh phải resolve conflict lớn về sau.

## 3. Quy ước đặt tên nhánh

| Loại                      | Prefix      | Dùng khi nào                                                            | Ví dụ                                   |
| ------------------------- | ----------- | ----------------------------------------------------------------------- | --------------------------------------- |
| Tính năng mới             | `feat/`     | Thêm chức năng, module, API mới                                         | `feat/user-crud`, `feat/order-checkout` |
| Sửa lỗi                   | `fix/`      | Sửa bug đã tồn tại trong `dev`                                          | `fix/login-null-pointer`                |
| Vá gấp production         | `hotfix/`   | Bug nghiêm trọng đang ở `main`, cần vá ngay không đợi release tiếp theo | `hotfix/payment-crash`                  |
| Việc vặt, không đổi logic | `chore/`    | Cập nhật dependency, cấu hình CI, dọn code                              | `chore/update-eslint`                   |
| Refactor                  | `refactor/` | Tái cấu trúc code, không đổi behavior                                   | `refactor/user-service`                 |
| Tài liệu                  | `docs/`     | Chỉ sửa README, comment, tài liệu                                       | `docs/api-guide`                        |

Quy tắc: chữ thường, cách nhau bằng dấu `-`, không dấu tiếng Việt, mô tả ngắn gọn nói lên nội dung (không đặt `feat/fix-bug-1`).

## 4. feat vs fix — khi nào dùng cái nào

- **feat**: bất cứ khi nào bạn thêm một khả năng/chức năng mới mà trước đó hệ thống chưa có (thêm API, thêm field, thêm màn hình, thêm nút bấm mới...).
- **fix**: khi bạn sửa lại một hành vi đang sai so với thiết kế/spec (bug), không thêm chức năng mới.
- Nếu một PR vừa thêm tính năng vừa tiện tay sửa vài bug nhỏ không liên quan → **tách ra 2 PR/2 nhánh riêng**, đừng gộp, để review và revert dễ dàng.
- `hotfix/` là trường hợp đặc biệt của `fix/` nhưng nhánh xuất phát từ `main` thay vì `dev`, vì cần release ngay lập tức không đợi `dev` gộp đủ tính năng.

## 5. Quy ước đặt tên commit (Conventional Commits)

Format:

```
<type>(<phạm-vi>): <mô tả ngắn, thì hiện tại, không viết hoa đầu, không chấm cuối>
```

Các `type` được dùng:

| type       | Ý nghĩa                                                |
| ---------- | ------------------------------------------------------ |
| `feat`     | Thêm tính năng mới                                     |
| `fix`      | Sửa bug                                                |
| `docs`     | Thay đổi tài liệu                                      |
| `style`    | Format code, dấu cách, không đổi logic                 |
| `refactor` | Tái cấu trúc code, không thêm tính năng, không sửa bug |
| `perf`     | Cải thiện hiệu năng                                    |
| `test`     | Thêm/sửa unit test                                     |
| `chore`    | Việc linh tinh: update dependency, cấu hình build/CI   |

Ví dụ:

```
feat(user): thêm API tạo user mới
fix(auth): sửa lỗi token hết hạn không redirect login
refactor(order): tách OrderService thành các use-case riêng
chore(ci): thêm bước chạy eslint vào pipeline
```

Quy tắc:

- Mỗi commit chỉ nên làm **một việc**. Đừng gộp "thêm API + sửa bug khác + format lại file" vào 1 commit.
- Không commit các message vô nghĩa kiểu `update`, `fix bug`, `wip`, `asdf` — nếu lỡ tạo trong lúc code thì dọn lại bằng `rebase -i` trước khi tạo PR (xem mục 6).

## 6. Khi nào rebase, khi nào không — tránh commit rác

**Trước khi tạo PR (bắt buộc):** dọn nhánh của mình bằng rebase tương tác để gộp các commit rác (`wip`, `fix typo`, `oops`) thành các commit có ý nghĩa:

```bash
git checkout feat/user-crud
git fetch origin
git rebase -i origin/dev
```

Trong màn hình interactive rebase:

- Giữ `pick` cho commit đầu tiên/commit chính.
- Đổi `pick` thành `squash` (hoặc `s`) cho các commit rác cần gộp vào commit trước nó.
- Sửa lại commit message cuối cùng cho đúng chuẩn ở mục 5.

**Khi nhánh `dev` đã có commit mới trong lúc bạn đang code:**

```bash
git fetch origin
git rebase origin/dev
```

→ Dùng rebase (không dùng `merge`) để giữ lịch sử thẳng, dễ đọc, tránh những merge-commit vô nghĩa kiểu "Merge branch dev into feature/x".

**Khi nào KHÔNG rebase:**

- Nhánh đã push lên remote và **có người khác cùng code chung** trên nhánh đó → rebase sẽ viết lại lịch sử, làm nhánh của người khác bị lệch. Trường hợp này ưu tiên `git merge origin/dev` vào nhánh của mình, hoặc thống nhất với người cùng nhánh trước khi rebase.
- Nhánh `dev` và `main` — **không bao giờ rebase hai nhánh chính này**, chỉ rebase nhánh `feature/*`, `fix/*` cá nhân.

**Quy tắc chung:** rebase nhánh của riêng mình thoải mái (chưa ai dựa vào nó), không rebase nhánh dùng chung.

## 7. Quy trình tạo PR

```bash
git push origin feature/user-crud
```

1. Tạo Pull Request trên GitHub, **base = `dev`** (kiểm tra kỹ vì `main` là default branch nên GitHub sẽ tự chọn `main`, phải đổi lại).
2. Điền đầy đủ checklist trong PR template (xem `.github/pull_request_template.md`).
3. Đảm bảo CI (build/test/lint) chạy xanh.
4. Tối thiểu 1 người LGTM (approve) mới được merge.
5. Merge xong → xoá nhánh (`git push origin --delete feature/user-crud` hoặc dùng nút "Delete branch" trên GitHub).

## 8. Sau khi merge, dọn máy local

```bash
git checkout dev
git pull origin dev
git branch -d feature/user-crud
```
