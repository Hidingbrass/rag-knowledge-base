#!/usr/bin/env python3
"""Build upload-ready PDF files for the enterprise technical-docs demo corpus."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from xml.sax.saxutils import escape

try:
    from pypdf import PdfReader
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_CENTER
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import mm
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import (
        PageBreak,
        Paragraph,
        Preformatted,
        SimpleDocTemplate,
        Spacer,
    )
except ImportError as error:  # pragma: no cover - local setup guidance
    raise SystemExit(
        "Missing PDF build dependency. Run: "
        "python -m pip install -r scripts/requirements-docs.txt"
    ) from error


ROOT_DIR = Path(__file__).resolve().parents[1]
CORPUS_DIR = ROOT_DIR / "demo" / "technical_docs"
MANIFEST_PATH = CORPUS_DIR / "manifest.json"
DEFAULT_FONT_PATHS = (
    Path("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
    Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
    Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
)


def find_cjk_font(explicit_font: str | None = None) -> Path:
    candidates = ([Path(explicit_font)] if explicit_font else []) + list(DEFAULT_FONT_PATHS)
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError(
        "No CJK font found. Pass --font /absolute/path/to/a/CJK-font.ttf"
    )


def register_fonts(font_path: Path) -> None:
    if "AIKBSans" not in pdfmetrics.getRegisteredFontNames():
        pdfmetrics.registerFont(TTFont("AIKBSans", str(font_path)))


def build_styles() -> dict[str, ParagraphStyle]:
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "AIKBTitle",
            parent=base["Title"],
            fontName="AIKBSans",
            fontSize=22,
            leading=29,
            textColor=colors.HexColor("#11233F"),
            alignment=TA_CENTER,
            spaceAfter=8 * mm,
        ),
        "h1": ParagraphStyle(
            "AIKBHeading1",
            parent=base["Heading1"],
            fontName="AIKBSans",
            fontSize=15.5,
            leading=21,
            textColor=colors.HexColor("#145C9E"),
            spaceBefore=4 * mm,
            spaceAfter=2 * mm,
            keepWithNext=True,
        ),
        "h2": ParagraphStyle(
            "AIKBHeading2",
            parent=base["Heading2"],
            fontName="AIKBSans",
            fontSize=12,
            leading=17,
            textColor=colors.HexColor("#173B57"),
            spaceBefore=3 * mm,
            spaceAfter=1.5 * mm,
            keepWithNext=True,
        ),
        "body": ParagraphStyle(
            "AIKBBody",
            parent=base["BodyText"],
            fontName="AIKBSans",
            fontSize=10,
            leading=15.5,
            textColor=colors.HexColor("#263444"),
            spaceAfter=2 * mm,
            wordWrap="CJK",
        ),
        "meta": ParagraphStyle(
            "AIKBMeta",
            parent=base["BodyText"],
            fontName="AIKBSans",
            fontSize=9.2,
            leading=14,
            textColor=colors.HexColor("#53657A"),
            leftIndent=5 * mm,
            bulletIndent=1.5 * mm,
            spaceAfter=1.2 * mm,
        ),
        "bullet": ParagraphStyle(
            "AIKBBullet",
            parent=base["BodyText"],
            fontName="AIKBSans",
            fontSize=9.8,
            leading=15,
            textColor=colors.HexColor("#263444"),
            leftIndent=7 * mm,
            firstLineIndent=-4 * mm,
            spaceAfter=1.5 * mm,
            wordWrap="CJK",
        ),
        "code": ParagraphStyle(
            "AIKBCode",
            parent=base["Code"],
            fontName="Courier",
            fontSize=8.3,
            leading=12,
            textColor=colors.HexColor("#17202A"),
            backColor=colors.HexColor("#F1F5F9"),
            borderColor=colors.HexColor("#D8E1EA"),
            borderWidth=0.5,
            borderPadding=7,
            spaceBefore=2 * mm,
            spaceAfter=3 * mm,
        ),
    }


def inline_markup(text: str) -> str:
    """Escape source text and render simple Markdown inline-code spans."""
    parts = text.split("`")
    rendered = []
    for index, part in enumerate(parts):
        escaped = escape(part)
        if index % 2:
            rendered.append(f'<font name="Courier" color="#8B2E2E">{escaped}</font>')
        else:
            rendered.append(escaped)
    return "".join(rendered)


def markdown_story(markdown_text: str, styles: dict[str, ParagraphStyle]) -> list:
    story = []
    code_lines: list[str] = []
    in_code = False
    metadata = True

    for raw_line in markdown_text.splitlines():
        line = raw_line.rstrip()
        if line.startswith("```"):
            if in_code:
                story.append(Preformatted("\n".join(code_lines), styles["code"] ))
                code_lines = []
            in_code = not in_code
            continue
        if in_code:
            code_lines.append(line)
            continue
        if not line:
            story.append(Spacer(1, 1.5 * mm))
            continue
        if line.startswith("# "):
            story.append(Paragraph(inline_markup(line[2:]), styles["title"]))
            metadata = True
            continue
        if line.startswith("## "):
            metadata = False
            story.append(Paragraph(inline_markup(line[3:]), styles["h1"]))
            continue
        if line.startswith("### "):
            metadata = False
            story.append(Paragraph(inline_markup(line[4:]), styles["h2"]))
            continue
        if line == "---PAGE---":
            story.append(PageBreak())
            continue
        if line.startswith("- "):
            style = styles["meta"] if metadata else styles["bullet"]
            story.append(Paragraph(f"• {inline_markup(line[2:])}", style))
            continue
        story.append(Paragraph(inline_markup(line), styles["body"]))

    if code_lines:
        story.append(Preformatted("\n".join(code_lines), styles["code"]))
    return story


def page_decorator(document_id: str, title: str):
    def draw_page(canvas, document):
        width, height = A4
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor("#D8E1EA"))
        canvas.setLineWidth(0.5)
        canvas.line(18 * mm, height - 16 * mm, width - 18 * mm, height - 16 * mm)
        canvas.setFont("AIKBSans", 8.2)
        canvas.setFillColor(colors.HexColor("#60758A"))
        canvas.drawString(18 * mm, height - 12.5 * mm, f"AIKB INTERNAL | {document_id}")
        canvas.drawRightString(width - 18 * mm, height - 12.5 * mm, title)
        canvas.line(18 * mm, 14 * mm, width - 18 * mm, 14 * mm)
        canvas.drawString(18 * mm, 9.5 * mm, "Enterprise Technical Knowledge Base Demo")
        canvas.drawRightString(width - 18 * mm, 9.5 * mm, f"Page {document.page}")
        canvas.restoreState()

    return draw_page


def build_pdf(source_path: Path, output_path: Path, document_id: str, title: str) -> int:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    document = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        rightMargin=18 * mm,
        leftMargin=18 * mm,
        topMargin=21 * mm,
        bottomMargin=18 * mm,
        title=title,
        author="AIKB Engineering",
        subject="Enterprise technical knowledge base demo corpus",
    )
    story = markdown_story(source_path.read_text(encoding="utf-8"), build_styles())
    decorate = page_decorator(document_id, title)
    document.build(story, onFirstPage=decorate, onLaterPages=decorate)

    reader = PdfReader(str(output_path))
    if not reader.pages:
        raise RuntimeError(f"generated PDF has no pages: {output_path}")
    extracted = "\n".join(page.extract_text() or "" for page in reader.pages)
    if document_id not in extracted:
        raise RuntimeError(f"generated PDF text check failed: {output_path}")
    return len(reader.pages)


def build_corpus(font_path: Path) -> list[dict]:
    register_fonts(font_path)
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    results = []
    for item in manifest["documents"]:
        source_path = CORPUS_DIR / item["source"]
        output_path = CORPUS_DIR / item["pdf"]
        if not source_path.exists():
            raise FileNotFoundError(f"missing corpus source: {source_path}")
        page_count = build_pdf(
            source_path,
            output_path,
            item["document_id"],
            item["title"],
        )
        results.append({
            "document_id": item["document_id"],
            "pdf": str(output_path.relative_to(ROOT_DIR)),
            "pages": page_count,
            "bytes": output_path.stat().st_size,
        })
    return results


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", help="Absolute path to a CJK TrueType font.")
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()
    selected_font = find_cjk_font(args.font)
    print(f"Using CJK font: {selected_font}")
    for result in build_corpus(selected_font):
        print(result)
