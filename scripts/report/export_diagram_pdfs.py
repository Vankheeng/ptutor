"""Package report diagrams into print-sized, single-page PDFs."""

from pathlib import Path
from PIL import Image
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A2, A4, landscape

ROOT = Path(__file__).resolve().parents[2] / "report" / "diagrams"
PAGES = {
    "uc-tong-quan": landscape(A2),
    "uc-ai-de-xuat": landscape(A4),
    # The detailed sequence has many participants; preserve its aspect ratio.
    "tuan-tu-ai-de-xuat": (2200, 1280),
    "tuan-tu-tu-hoc-trong-so": (2000, 1260),
    "tuan-tu-phan-tich-nhu-cau": (1250, 550),
    "tuan-tu-tuong-tac-ket-qua": (1300, 750),
}

for stem, page_size in PAGES.items():
    png = ROOT / f"{stem}.png"
    pdf = ROOT / f"{stem}.pdf"
    with Image.open(png) as image:
        width, height = image.size
    page_w, page_h = page_size
    margin = 20
    scale = min((page_w - 2 * margin) / width, (page_h - 2 * margin) / height)
    draw_w, draw_h = width * scale, height * scale
    doc = canvas.Canvas(str(pdf), pagesize=page_size)
    doc.drawImage(str(png), (page_w - draw_w) / 2, (page_h - draw_h) / 2,
                  width=draw_w, height=draw_h, mask='auto')
    doc.showPage()
    doc.save()
    print(pdf)
