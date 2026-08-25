# Ptutor

Dự án kết nối học viên gia sư

## Cài đặt

```bash
npm install
```

Lệnh trên tự cài toàn bộ dependency và tự bật git hooks (husky) — không cần chạy thêm lệnh nào khác.

> Dùng pnpm: nếu hook không tự bật, chạy thêm `pnpm approve-builds` và chọn approve cho `husky`.

## Các lệnh thường dùng

| Lệnh                   | Chức năng                         |
| ---------------------- | --------------------------------- |
| `npm run lint`         | Kiểm tra lỗi code bằng ESLint     |
| `npm run lint:fix`     | Tự sửa lỗi ESLint có thể fix được |
| `npm run format`       | Tự format code bằng Prettier      |
| `npm run format:check` | Kiểm tra format mà không sửa      |
| `npm test`             | Chạy unit test                    |

## Git hooks tự động (husky)

Khi bạn `git commit` / `git push`, các hook sau tự chạy, không cần làm gì thêm:

- **pre-commit**: tự lint + format file đang staged
- **commit-msg**: chặn commit message không đúng chuẩn `type(scope): mô tả` (xem quy ước bên dưới)
- **pre-push**: chạy toàn bộ unit test, push bị huỷ nếu test fail

## Quy ước nhánh & commit

Xem chi tiết đầy đủ trong [`CONTRIBUTING.md`](./CONTRIBUTING.md) — bao gồm:

- Sơ đồ nhánh `main` → `dev` → `feat/*`
- Quy ước đặt tên nhánh (`feat/`, `fix/`, `hotfix/`...)
- Chuẩn commit message (`feat`, `fix`, `docs`...)
- Khi nào rebase, khi nào merge
- Quy trình tạo Pull Request

## Cấu trúc file cấu hình

| File                                 | Vai trò                                          |
| ------------------------------------ | ------------------------------------------------ |
| `eslint.config.js`                   | Rule kiểm tra lỗi code                           |
| `.prettierrc.json`                   | Rule format code                                 |
| `commitlint.config.js`               | Rule kiểm tra commit message                     |
| `.github/workflows/branch_check.yml` | CI: kiểm tra tên nhánh và base branch khi tạo PR |
| `.github/pull_request_template.md`   | Checklist hiện tự động khi tạo PR                |
