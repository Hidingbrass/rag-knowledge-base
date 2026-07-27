from app.services.sparse_embedding_service import (
    encode_sparse_text,
    stable_sparse_index,
    tokenize_lexical_features,
)


def test_mixed_language_tokenization_keeps_technical_terms_and_chinese_ngrams():
    features = tokenize_lexical_features("Redis 用于知识库 cache")

    assert "term:redis" in features
    assert "term:cache" in features
    assert "zh2:知识" in features
    assert "zh2:识库" in features
    assert "zh3:知识库" in features


def test_sparse_indices_are_deterministic_and_sorted():
    first = encode_sparse_text("Qdrant 向量检索")
    second = encode_sparse_text("Qdrant 向量检索")

    assert first == second
    assert first.indices == sorted(first.indices)
    assert len(first.indices) == len(first.values)
    assert stable_sparse_index("term:qdrant") in first.indices


def test_repeated_terms_use_saturated_term_frequency():
    once = encode_sparse_text("Redis")
    repeated = encode_sparse_text("Redis Redis Redis")

    index = stable_sparse_index("term:redis")
    once_value = once.values[once.indices.index(index)]
    repeated_value = repeated.values[repeated.indices.index(index)]

    assert repeated_value > once_value
    assert repeated_value < once_value * 3


def test_empty_text_returns_empty_sparse_vector():
    assert encode_sparse_text("").indices == []
    assert encode_sparse_text("").values == []
