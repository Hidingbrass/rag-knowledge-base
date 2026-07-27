"""Lightweight sparse lexical encoding for Qdrant hybrid retrieval.

The dense branch is still produced by the DashScope embedding model.  This
module supplies the complementary lexical branch without introducing another
runtime model download:

- Latin words and technical tokens are normalized to lower case.
- Continuous Chinese text is represented with character bi-grams and
  tri-grams, which avoids relying on whitespace segmentation.
- Tokens are mapped to deterministic integer dimensions with BLAKE2b.
- Repeated terms use BM25-style term-frequency saturation.  Collection-level
  IDF weighting is applied by Qdrant's sparse-vector ``IDF`` modifier.

This is deliberately described as sparse lexical retrieval, not as a complete
BM25 implementation: document-length normalization is not calculated here.
"""

from __future__ import annotations

import hashlib
import re
from collections import Counter, defaultdict
from dataclasses import dataclass


TOKEN_PATTERN = re.compile(r"[a-zA-Z0-9_+#.-]+|[\u4e00-\u9fff]+")
MAX_SPARSE_INDEX = 2_147_483_647
BM25_K1 = 1.2


@dataclass(frozen=True)
class SparseLexicalVector:
    indices: list[int]
    values: list[float]


def tokenize_lexical_features(text: str) -> list[str]:
    """Return stable lexical features for mixed Chinese/English text."""
    features: list[str] = []

    for match in TOKEN_PATTERN.finditer(text or ""):
        token = match.group(0)
        if re.fullmatch(r"[\u4e00-\u9fff]+", token):
            if len(token) == 1:
                features.append(f"zh1:{token}")
                continue

            for size in (2, 3):
                if len(token) < size:
                    continue
                features.extend(
                    f"zh{size}:{token[index:index + size]}"
                    for index in range(len(token) - size + 1)
                )
        else:
            normalized = token.strip(".-").lower()
            if normalized:
                features.append(f"term:{normalized}")

    return features


def stable_sparse_index(token: str) -> int:
    """Map a token to the same positive sparse dimension on every process."""
    digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, byteorder="big") % MAX_SPARSE_INDEX


def _saturated_term_frequency(frequency: int) -> float:
    return frequency * (BM25_K1 + 1.0) / (frequency + BM25_K1)


def encode_sparse_text(text: str) -> SparseLexicalVector:
    """Encode text as sorted Qdrant-compatible sparse indices and values."""
    token_counts = Counter(tokenize_lexical_features(text))
    bucket_counts: dict[int, int] = defaultdict(int)

    for token, count in token_counts.items():
        bucket_counts[stable_sparse_index(token)] += count

    indices = sorted(bucket_counts)
    values = [
        round(_saturated_term_frequency(bucket_counts[index]), 6)
        for index in indices
    ]
    return SparseLexicalVector(indices=indices, values=values)
