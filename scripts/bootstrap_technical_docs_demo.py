#!/usr/bin/env python3
"""Create a technical-docs knowledge base and upload the demo PDF corpus."""

from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path

import requests


ROOT_DIR = Path(__file__).resolve().parents[1]
CORPUS_DIR = ROOT_DIR / "demo" / "technical_docs"
MANIFEST_PATH = CORPUS_DIR / "manifest.json"


def load_pdf_assets(manifest_path: Path = MANIFEST_PATH) -> list[Path]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    pdf_paths = [CORPUS_DIR / document["pdf"] for document in manifest["documents"]]
    missing = [str(path) for path in pdf_paths if not path.is_file()]
    if missing:
        raise FileNotFoundError(
            "Missing generated PDFs. Run scripts/build_technical_docs_pdfs.py first: "
            + ", ".join(missing)
        )
    return pdf_paths


def unwrap_api_response(response: requests.Response) -> dict:
    response.raise_for_status()
    payload = response.json()
    if not payload.get("success", False):
        raise RuntimeError(payload.get("message") or "AIKB API request failed")
    return payload.get("data") or {}


def create_knowledge_base(
        session: requests.Session,
        base_url: str,
        token: str,
        name: str,
        description: str,
        timeout: float,
) -> str:
    response = session.post(
        f"{base_url}/api/knowledge-bases",
        headers={"Authorization": f"Bearer {token}"},
        json={"name": name, "description": description},
        timeout=timeout,
    )
    data = unwrap_api_response(response)
    knowledge_base_id = data.get("id")
    if not knowledge_base_id:
        raise RuntimeError("knowledge-base response does not contain id")
    return knowledge_base_id


def upload_pdf(
        session: requests.Session,
        base_url: str,
        token: str,
        knowledge_base_id: str,
        pdf_path: Path,
        timeout: float,
) -> dict:
    with pdf_path.open("rb") as pdf_file:
        response = session.post(
            f"{base_url}/api/knowledge-bases/{knowledge_base_id}/documents",
            headers={"Authorization": f"Bearer {token}"},
            files={"file": (pdf_path.name, pdf_file, "application/pdf")},
            timeout=timeout,
        )
    return unwrap_api_response(response)


def bootstrap_demo(args) -> dict:
    pdf_paths = load_pdf_assets()
    if args.dry_run:
        return {
            "dry_run": True,
            "document_count": len(pdf_paths),
            "documents": [path.name for path in pdf_paths],
        }
    if not args.token:
        raise ValueError(
            "An access token is required. Pass --token or set AIKB_ACCESS_TOKEN."
        )

    base_url = args.base_url.rstrip("/")
    session = requests.Session()
    knowledge_base_id = args.knowledge_base_id or create_knowledge_base(
        session,
        base_url,
        args.token,
        args.name,
        args.description,
        args.timeout,
    )

    uploaded = []
    for index, pdf_path in enumerate(pdf_paths):
        data = upload_pdf(
            session,
            base_url,
            args.token,
            knowledge_base_id,
            pdf_path,
            args.timeout,
        )
        uploaded.append({
            "filename": pdf_path.name,
            "document_id": data.get("fastApiDocumentId"),
            "chunk_count": data.get("chunkCount"),
            "status": data.get("status"),
            "duplicated": data.get("duplicated", False),
        })
        if index < len(pdf_paths) - 1 and args.delay_seconds > 0:
            time.sleep(args.delay_seconds)

    return {
        "dry_run": False,
        "base_url": base_url,
        "knowledge_base_id": knowledge_base_id,
        "document_count": len(uploaded),
        "documents": uploaded,
    }


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base-url",
        default=os.getenv("BACKEND_URL", "http://127.0.0.1:8080"),
    )
    parser.add_argument("--token", default=os.getenv("AIKB_ACCESS_TOKEN"))
    parser.add_argument(
        "--knowledge-base-id",
        help="Reuse an existing knowledge base instead of creating one.",
    )
    parser.add_argument("--name", default="AIKB 平台技术文档")
    parser.add_argument(
        "--description",
        default="企业内部 AI/RAG 平台架构、接口、安全、运维和评测文档",
    )
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=13.0,
        help="Delay between uploads; the default respects the 5/minute AI rate limit.",
    )
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


if __name__ == "__main__":
    result = bootstrap_demo(parse_args())
    print(json.dumps(result, ensure_ascii=False, indent=2))
