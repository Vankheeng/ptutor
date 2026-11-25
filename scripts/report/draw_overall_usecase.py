"""Draw the PTutor overview use-case diagram as a report-ready PNG."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "report" / "diagrams" / "uc-tong-quan.png"
FONT = Path(r"C:\Windows\Fonts\arial.ttf")
BOLD = Path(r"C:\Windows\Fonts\arialbd.ttf")

W, H = 7016, 4961  # A2 landscape at 300 dpi; 6 roles and all menu modules
INK = "#241945"
PURPLE = "#6D4CC4"
LINE = "#9B8CC9"
LIGHT = "#F9F7FF"

groups = [
    ("Khách", [
        "Đăng ký tài khoản", "Đăng nhập", "Khôi phục mật khẩu",
    ]),
    ("Học viên", [
        "Đăng và quản lý yêu cầu học", "Tìm kiếm gia sư",
        "Nhận đề xuất gia sư AI", "Gửi đề nghị học cho gia sư",
        "Phản hồi đề nghị từ gia sư", "Quản lý ứng tuyển của gia sư",
        "Tạo, ký và theo dõi hợp đồng", "Xem lịch và xác nhận buổi học",
        "Thanh toán học phí, nạp ví", "Gửi yêu cầu hoàn tiền",
        "Gửi và theo dõi khiếu nại", "Đánh giá gia sư",
        "Xem thông báo", "Gửi yêu cầu hỗ trợ",
        "Xem ví, lịch sử giao dịch", "Xem phân tích thu chi",
        "Quản lý hồ sơ, tài khoản ngân hàng", "Trao đổi với trợ lý AI",
    ]),
    ("Gia sư", [
        "Quản lý hồ sơ gia sư", "Quản lý chứng chỉ",
        "Đăng và quản lý yêu cầu dạy", "Tìm kiếm yêu cầu học",
        "Nhận đề xuất học viên AI", "Ứng tuyển yêu cầu học",
        "Phản hồi đề nghị từ học viên", "Tạo, ký và theo dõi hợp đồng",
        "Tạo và cập nhật buổi dạy", "Xem lịch dạy",
        "Phản hồi yêu cầu hoàn tiền", "Gửi và theo dõi khiếu nại",
        "Xem thông báo", "Gửi yêu cầu hỗ trợ",
        "Xem ví, lịch sử giao dịch", "Yêu cầu rút tiền",
        "Quản lý tài khoản ngân hàng", "Xem phân tích thu chi",
        "Trao đổi với trợ lý AI",
    ]),
    ("CSKH", [
        "Quản lý học viên", "Quản lý gia sư", "Theo dõi hợp đồng",
        "Quản lý thông báo", "Duyệt hồ sơ gia sư",
        "Duyệt chứng chỉ", "Duyệt yêu cầu dạy",
        "Duyệt yêu cầu học", "Quản lý danh mục học tập",
        "Xử lý khiếu nại", "Trả lời yêu cầu hỗ trợ",
    ]),
    ("Kế toán", [
        "Xử lý yêu cầu hoàn tiền", "Xử lý yêu cầu rút tiền",
        "Quản lý ví", "Xem lịch sử giao dịch",
    ]),
    ("Quản trị viên", [
        "Xem thống kê hệ thống", "Quản lý nhân viên",
        "Cấu hình trọng số gợi ý AI", "Quản lý kho tri thức",
        "Xử lý câu hỏi chưa trả lời", "Xem thống kê trợ lý AI",
    ]),
]

im = Image.new("RGB", (W, H), "white")
d = ImageDraw.Draw(im)
font_title = ImageFont.truetype(str(BOLD), 68)
font_actor = ImageFont.truetype(str(BOLD), 52)
font_uc = ImageFont.truetype(str(FONT), 46)

def center_text(box, value, font, fill=INK, spacing=7):
    x0, y0, x1, y1 = box
    max_width = x1 - x0 - 34
    words = value.split()
    lines, line = [], ""
    for word in words:
        trial = f"{line} {word}".strip()
        if d.textbbox((0, 0), trial, font=font)[2] > max_width and line:
            lines.append(line)
            line = word
        else:
            line = trial
    if line:
        lines.append(line)
    heights = [d.textbbox((0, 0), text, font=font)[3] - d.textbbox((0, 0), text, font=font)[1] for text in lines]
    total = sum(heights) + spacing * (len(lines) - 1)
    y = (y0 + y1 - total) / 2
    for text, height in zip(lines, heights):
        width = d.textbbox((0, 0), text, font=font)[2]
        d.text(((x0 + x1 - width) / 2, y), text, font=font, fill=fill)
        y += height + spacing

def actor(cx, cy):
    d.ellipse((cx - 25, cy - 46, cx + 25, cy + 4), outline=INK, width=4)
    d.line((cx, cy + 4, cx, cy + 102), fill=INK, width=4)
    d.line((cx - 54, cy + 43, cx + 54, cy + 43), fill=INK, width=4)
    d.line((cx, cy + 102, cx - 43, cy + 151), fill=INK, width=4)
    d.line((cx, cy + 102, cx + 43, cy + 151), fill=INK, width=4)

d.rounded_rectangle((35, 285, W - 35, H - 35), radius=22, outline=LINE, width=4)
d.rectangle((W // 2 - 430, 264, W // 2 + 430, 316), fill="white")
center_text((W // 2 - 430, 260, W // 2 + 430, 320), "HỆ THỐNG PTUTOR", font_title)

left, gap = 65, 24
col_w = (W - 2 * left - 5 * gap) / 6
for i, (role, cases) in enumerate(groups):
    x0 = int(left + i * (col_w + gap))
    x1 = int(x0 + col_w)
    cx = (x0 + x1) // 2
    actor(cx, 67)
    center_text((x0, 215, x1, 270), role, font_actor)
    d.rounded_rectangle((x0, 350, x1, H - 80), radius=25, outline="#E2DCF4", width=3)
    d.rounded_rectangle((x0 + 12, 370, x1 - 12, 460), radius=18, fill="#EEE8FC")
    center_text((x0 + 12, 370, x1 - 12, 460), role.upper(), font_actor, PURPLE)

    spine = x0 + 41
    y_top = 550
    y_step = 224
    oval_left = x0 + 80
    oval_right = x1 - 17
    oval_h = 182
    first_center = y_top + oval_h / 2
    last_center = y_top + (len(cases) - 1) * y_step + oval_h / 2
    d.line((cx, 273, cx, 335, spine, 335, spine, last_center), fill=LINE, width=4)
    for j, uc in enumerate(cases):
        y0 = y_top + j * y_step
        y1 = y0 + oval_h
        mid = (y0 + y1) / 2
        d.line((spine, mid, oval_left, mid), fill=LINE, width=4)
        d.ellipse((oval_left, y0, oval_right, y1), fill=LIGHT, outline=PURPLE, width=4)
        center_text((oval_left + 15, y0 + 13, oval_right - 15, y1 - 13), uc, font_uc)

OUT.parent.mkdir(parents=True, exist_ok=True)
im.save(OUT, optimize=True)
print(OUT)
