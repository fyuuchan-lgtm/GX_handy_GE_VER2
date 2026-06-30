import sys
import pdfplumber

src = sys.argv[1]
dst = sys.argv[2]

with pdfplumber.open(src) as pdf:
    with open(dst, "w", encoding="utf-8") as out:
        for i, page in enumerate(pdf.pages, start=1):
            out.write(f"\n===== PAGE {i} =====\n")
            text = page.extract_text() or ""
            out.write(text)
            out.write("\n")
    print(f"OK: {len(pdf.pages)} pages -> {dst}")
