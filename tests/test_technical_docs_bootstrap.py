import pytest

from scripts.bootstrap_technical_docs_demo import bootstrap_demo, parse_args


def test_bootstrap_dry_run_validates_all_generated_pdfs():
    result = bootstrap_demo(parse_args(["--dry-run"]))

    assert result["dry_run"] is True
    assert result["document_count"] == 6
    assert all(filename.endswith(".pdf") for filename in result["documents"])


def test_bootstrap_requires_token_before_online_requests():
    args = parse_args([])
    args.token = None

    with pytest.raises(ValueError, match="access token"):
        bootstrap_demo(args)
