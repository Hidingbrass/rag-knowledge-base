import json
from pathlib import Path

from pypdf import PdfReader

from app.evaluation.datasets import (
    TECHNICAL_DOCS_DATASET_PATH,
    load_evaluation_cases,
    load_technical_docs_dataset,
)


ROOT_DIR = Path(__file__).resolve().parents[1]
CORPUS_DIR = ROOT_DIR / "demo" / "technical_docs"


def test_technical_docs_dataset_distribution_and_ids():
    cases = load_technical_docs_dataset()
    answerable = [case for case in cases if not case["should_reject"]]
    rejection = [case for case in cases if case["should_reject"]]

    assert len(cases) == 26
    assert len(answerable) == 20
    assert len(rejection) == 6
    assert len({case["id"] for case in cases}) == len(cases)


def test_dataset_loader_keeps_legacy_and_technical_docs_separate():
    assert load_evaluation_cases("legacy")
    assert load_evaluation_cases("technical_docs") == load_technical_docs_dataset()
    assert TECHNICAL_DOCS_DATASET_PATH.is_file()


def test_gold_documents_exist_and_contain_expected_keywords():
    manifest = json.loads((CORPUS_DIR / "manifest.json").read_text(encoding="utf-8"))
    source_by_pdf = {
        Path(item["pdf"]).name: CORPUS_DIR / item["source"]
        for item in manifest["documents"]
    }

    for case in load_technical_docs_dataset():
        if case["should_reject"]:
            continue
        gold_text = "\n".join(
            source_by_pdf[filename].read_text(encoding="utf-8")
            for filename in case["gold_documents"]
        ).lower()
        for keyword in case["expected_keywords"]:
            assert keyword.lower() in gold_text, (case["id"], keyword)


def test_generated_pdfs_are_extractable_and_match_manifest():
    manifest = json.loads((CORPUS_DIR / "manifest.json").read_text(encoding="utf-8"))

    for item in manifest["documents"]:
        pdf_path = CORPUS_DIR / item["pdf"]
        assert pdf_path.is_file()
        reader = PdfReader(str(pdf_path))
        assert len(reader.pages) == 2
        extracted = "\n".join(page.extract_text() or "" for page in reader.pages)
        assert item["document_id"] in extracted
