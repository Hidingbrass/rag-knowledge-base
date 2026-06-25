from app.evaluation import evaluate_generation_rerank as generation_rerank_module


def test_evaluate_generation_rerank_passes_hybrid_config(monkeypatch):
    captured_requests = []

    test_cases = [
        {
            "question": "Redis usage?",
            "should_reject": False,
            "expected_keywords": ["Redis"],
        }
    ]

    def fake_rag_chat_with_rerank_response(request):
        captured_requests.append(request)
        return {
            "answer": "Redis is used for cache [1].",
            "sources": [
                {
                    "text": "Redis is used for cache.",
                    "filename": "demo.pdf",
                    "page_number": 1,
                    "chunk_index": 0,
                    "vector_score": 0.8,
                    "keyword_score": 1,
                    "rerank_score": 0.9,
                }
            ],
            "retrieval_mode": "rerank",
            "rerank_error": None,
            "rerank_elapsed_seconds": 0.1,
        }

    monkeypatch.setattr(generation_rerank_module, "TEST_CASES", test_cases)
    monkeypatch.setattr(
        generation_rerank_module,
        "rag_chat_with_rerank_response",
        fake_rag_chat_with_rerank_response,
    )

    evaluation = generation_rerank_module.evaluate_generation_rerank(
        candidate_k=6,
        rerank_top_k=3,
        rerank_min_score=0.75,
        retrieval_mode="hybrid",
        keyword_limit=5,
    )

    request = captured_requests[0]

    assert request.retrieval_mode == "hybrid"
    assert request.keyword_limit == 5
    assert evaluation["config"]["retrieval_mode"] == "hybrid"
    assert evaluation["config"]["keyword_limit"] == 5
    assert evaluation["results"][0]["candidate_retrieval_mode"] == "hybrid"
    assert evaluation["results"][0]["retrieval_mode"] == "rerank"
