"""Draw the recommendation use cases with UML actors and a system boundary."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "report" / "diagrams" / "uc-ai-de-xuat.png"
W, H = 3508, 2480  # A4 landscape, 300 dpi
INK = "#172B3B"
BLUE = "#2D7397"
SYSTEM = "#EAF7FD"
CASE = "#FFFFFF"
FONT = Path(r"C:\Windows\Fonts\arial.ttf")
BOLD = Path(r"C:\Windows\Fonts\arialbd.ttf")

image = Image.new("RGB", (W, H), "white")
draw = ImageDraw.Draw(image)
font_title = ImageFont.truetype(str(BOLD), 50)
font_case = ImageFont.truetype(str(FONT), 43)
font_actor = ImageFont.truetype(str(FONT), 43)
font_note = ImageFont.truetype(str(FONT), 34)


def centered_text(box, value, font, fill=INK):
    x0, y0, x1, y1 = box
    lines = value.split("\n")
    line_height = 52 if font == font_case else 48
    top = (y0 + y1 - line_height * len(lines)) / 2
    for line in lines:
        width = draw.textbbox((0, 0), line, font=font)[2]
        draw.text(((x0 + x1 - width) / 2, top), line, font=font, fill=fill)
        top += line_height


def actor(x, y, label):
    """Draw a standard stick actor; y is the shoulder level."""
    draw.ellipse((x - 23, y - 100, x + 23, y - 54), outline=INK, width=5)
    draw.line((x, y - 54, x, y + 55), fill=INK, width=5)
    draw.line((x - 55, y - 6, x + 55, y - 6), fill=INK, width=5)
    draw.line((x, y + 55, x - 46, y + 116), fill=INK, width=5)
    draw.line((x, y + 55, x + 46, y + 116), fill=INK, width=5)
    centered_text((x - 145, y + 126, x + 145, y + 190), label, font_actor)


def use_case(box, label):
    draw.ellipse(box, fill=CASE, outline=BLUE, width=5)
    centered_text(box, label, font_case)


def association(start, end):
    draw.line((*start, *end), fill=INK, width=5)


def generalization(start, tip):
    """A solid UML generalization line ending in an unfilled triangle."""
    dx, dy = tip[0] - start[0], tip[1] - start[1]
    length = (dx * dx + dy * dy) ** 0.5
    ux, uy = dx / length, dy / length
    base = (tip[0] - ux * 56, tip[1] - uy * 56)
    perp = (-uy * 22, ux * 22)
    association(start, base)
    draw.polygon(
        [tip, (base[0] + perp[0], base[1] + perp[1]),
         (base[0] - perp[0], base[1] - perp[1])],
        fill=SYSTEM, outline=INK, width=5,
    )


def dependency_up(x, bottom, top):
    """A dashed dependency from an internal process toward a later use case."""
    y = top + 30
    while y < bottom:
        draw.line((x, y, x, min(y + 25, bottom)), fill=INK, width=4)
        y += 42
    draw.line((x, top, x - 21, top + 28), fill=INK, width=4)
    draw.line((x, top, x + 21, top + 28), fill=INK, width=4)


# One system boundary, with every actor outside it.
draw.rectangle((400, 125, 3108, 2350), fill=SYSTEM, outline=BLUE, width=6)
centered_text((910, 135, 2598, 240), "HỆ THỐNG PTUTOR – AI ĐỀ XUẤT", font_title)

use_case((1250, 265, 2258, 430), "Gợi ý gia sư / học viên")
student_main = (650, 600, 1510, 760)
tutor_main = (1998, 600, 2858, 760)
use_case(student_main, "Nhận đề xuất gia sư")
use_case(tutor_main, "Nhận đề xuất học viên")
generalization((1080, 600), (1530, 405))
generalization((2428, 600), (1978, 405))
association((225, 700), (650, 680))
association((3283, 700), (2858, 680))

use_case((1250, 920, 2258, 1085), "Tự học trọng số\n«tác vụ nội bộ»")
dependency_up(1754, 920, 430)
centered_text((1790, 785, 2240, 900), "Cung cấp trọng số\nkhi chế độ ACTIVE", font_note)

student_cases = [
    "Phân tích nhu cầu học\nbằng câu tự nhiên",
    "Xem lý do phù hợp",
    "Xem thêm kết quả đề xuất",
    "Tinh chỉnh kết quả đề xuất",
    "Lưu bộ tìm kiếm gia sư",
]
tutor_cases = [
    "Phân tích nhu cầu dạy\nbằng câu tự nhiên",
    "Xem lý do phù hợp",
    "Xem thêm kết quả đề xuất",
    "Tinh chỉnh kết quả đề xuất",
    "Lưu bộ tìm kiếm học viên",
]
rows = [1170, 1380, 1590, 1800, 2010]
for y, student_label, tutor_label in zip(rows, student_cases, tutor_cases):
    student_box = (650, y, 1510, y + 145)
    tutor_box = (1998, y, 2858, y + 145)
    use_case(student_box, student_label)
    use_case(tutor_box, tutor_label)
    association((225, 700), (student_box[0], y + 72))
    association((3283, 700), (tutor_box[2], y + 72))

actor(170, 700, "Học viên")
actor(3338, 700, "Gia sư")

OUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUT, optimize=True)
print(OUT)
