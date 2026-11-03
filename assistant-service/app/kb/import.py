"""Cho phép chạy `python -m app.kb.import` đúng như thiết kế 03 mục 3.6.

`import` là từ khoá Python nên không `import app.kb.import` được; logic nằm ở `app/kb/importer.py`.
"""

from app.kb.importer import main

if __name__ == "__main__":
    main()
