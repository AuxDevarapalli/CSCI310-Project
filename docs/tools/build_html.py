"""Turns docs/implementation-document.md into a print-ready HTML file.

Usage (from the repository root):
    pip install markdown
    python docs/tools/build_html.py
    node docs/tools/html_to_pdf.js      # needs: npm install playwright && npx playwright install chromium
Any other Markdown-to-PDF tool also works; this script only adds the styling.
"""
import pathlib
import markdown

DOCS = pathlib.Path(__file__).resolve().parent.parent
SOURCE = DOCS / "implementation-document.md"
OUTPUT = DOCS / "implementation-document.html"

CSS = """
@page { size: Letter; margin: 0.75in 0.75in 0.8in 0.75in; }
@page landscape { size: Letter landscape; margin: 0.45in 0.5in 0.6in 0.5in; }
html { font-family: "DejaVu Serif", "Times New Roman", serif; font-size: 11pt; color: #1b1b1b; }
body { margin: 0; line-height: 1.42; }
h1 { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 19pt; margin: 0 0 10pt;
     padding-bottom: 4pt; border-bottom: 2px solid #990000; color: #111;
     break-before: page; }
h2 { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 13.5pt; margin: 16pt 0 6pt; color: #990000; }
p { margin: 0 0 7pt; }
ul, ol { margin: 0 0 8pt 18pt; padding: 0; }
li { margin-bottom: 3pt; }
code { font-family: "DejaVu Sans Mono", monospace; font-size: 8.6pt; background: #f4f1ee;
       padding: 0 2px; border-radius: 2px; }
table { border-collapse: collapse; width: 100%; margin: 6pt 0 11pt; font-size: 8.9pt;
        line-height: 1.32; break-inside: auto; }
th, td { border: 1px solid #b9b2ad; padding: 3.5pt 5pt; vertical-align: top; text-align: left; }
th { background: #f3e5e5; font-family: "DejaVu Sans", Arial, sans-serif; }
tr { break-inside: avoid; }
td code, th code { font-size: 8pt; }
.tag { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 7pt; font-weight: bold;
       padding: 0.5pt 3pt; border-radius: 3px; border: 1px solid; white-space: nowrap; }
.tag.new { color: #1b5e20; background: #d9f2de; border-color: #2e7d32; }
.tag.chg { color: #8a4600; background: #ffe7c2; border-color: #c76e00; }
.tag.same { color: #555; background: #f2f2f2; border-color: #999; }
.swatch { display: inline-block; width: 10pt; height: 10pt; border: 1px solid #555;
          vertical-align: -1pt; }
.swatch.new { background: #d9f2de; }
.swatch.chg { background: #ffe7c2; }
.figure { text-align: center; margin: 8pt 0 12pt; break-inside: avoid; }
.figure img { max-width: 100%; max-height: 8.6in; }
.figure.landscape { page: landscape; break-before: page; margin: 0; }
.figure.landscape img { max-height: 6.9in; }
.wide { page: landscape; }
.keep { break-inside: avoid; }
.wide table { font-size: 9pt; }
h1, h2 { break-after: avoid; }
.caption { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 8.8pt; color: #444;
           margin-top: 4pt; }
.title-page { height: 9.2in; display: flex; flex-direction: column; justify-content: center;
              text-align: center; }
.title-team { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 15pt; color: #990000;
              letter-spacing: 1px; margin-bottom: 8pt; }
.title-main { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 26pt; font-weight: bold;
              margin-bottom: 8pt; }
.title-sub { font-size: 13pt; color: #444; margin-bottom: 40pt; }
.title-authors { font-size: 11pt; line-height: 1.7; margin: 0 0.4in 16pt; }
.title-customer { font-size: 11pt; color: #444; }
.toc-page { break-before: page; font-size: 12pt; }
.toc-page ol { margin-top: 10pt; line-height: 1.9; }
"""

def main():
    text = SOURCE.read_text(encoding="utf-8")
    body = markdown.markdown(text, extensions=["tables", "md_in_html", "sane_lists"])
    html = (
        "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
        "<title>SplitSmart Implementation Document — Team 04</title>"
        f"<style>{CSS}</style></head><body>\n{body}\n</body></html>\n"
    )
    OUTPUT.write_text(html, encoding="utf-8")
    print(f"wrote {OUTPUT}")

if __name__ == "__main__":
    main()
