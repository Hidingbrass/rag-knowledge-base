from app.evaluation import evaluate_generation as generation_module


def test_evaluate_generation_counts_chunk_support(monkeypatch):
    test_cases = [
        {
            "question": "full support question",
            "should_reject": False,
            "expected_keywords": ["Alpha", "Beta"],
        },
        {
            "question": "partial support question",
            "should_reject": False,
            "expected_keywords": ["Gamma", "Delta"],
        },
    ]

    responses = {
        "full support question": {
            "answer": "Alpha and Beta are mentioned [1].",
            "sources": [
                {"text": "Alpha and Beta are both in this chunk."},
            ],
        },
        "partial support question": {
            "answer": "Gamma and Delta are mentioned [1].",
            "sources": [
                {"text": "Only Gamma is in this chunk."},
            ],
        },
    }

    def fake_rag_chat_response(request):
        return responses[request.question]

    monkeypatch.setattr(generation_module, "TEST_CASES", test_cases)
    monkeypatch.setattr(
        generation_module,
        "rag_chat_response",
        fake_rag_chat_response,
    )

    evaluation = generation_module.evaluate_generation()
    metrics = evaluation["metrics"]

    assert metrics["chunk_support_count"] == 1
    assert metrics["chunk_support_pass_rate"] == 0.5
    assert metrics["average_citation_support"] == 0.75
