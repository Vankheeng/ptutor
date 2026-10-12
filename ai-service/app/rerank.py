"""Cross-encoder relevance scoring with bge-reranker-v2-m3 (ONNX Runtime, int8)."""

from pathlib import Path

import numpy as np

from .config import RERANK_MODEL_FILE, RERANK_MODEL_VERSION

# XLM-RoBERTa vocabulary used by bge-reranker-v2-m3.
BOS_ID = 0
PAD_ID = 1
EOS_ID = 2


def build_pair(query_ids: list[int], document_ids: list[int], max_tokens: int) -> list[int]:
    """`<s> query </s></s> document </s>` with each side cut to `max_tokens` tokens."""
    return [BOS_ID, *query_ids[:max_tokens], EOS_ID, EOS_ID, *document_ids[:max_tokens], EOS_ID]


def sigmoid(logits: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-logits.astype(np.float64)))


class Reranker:
    model_version = RERANK_MODEL_VERSION

    def __init__(self, rerank_dir: Path, max_tokens: int = 256, threads: int = 0, batch_size: int = 4):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        self._tokenizer = Tokenizer.from_file(str(rerank_dir / "tokenizer.json"))
        self._tokenizer.no_truncation()
        self._tokenizer.no_padding()
        self._max_tokens = max_tokens
        self._batch_size = max(1, batch_size)

        options = ort.SessionOptions()
        if threads > 0:
            options.intra_op_num_threads = threads
        self._session = ort.InferenceSession(
            str(rerank_dir / RERANK_MODEL_FILE), sess_options=options, providers=["CPUExecutionProvider"]
        )
        self._input_names = {i.name for i in self._session.get_inputs()}

    def score(self, query: str, documents: list[str]) -> list[float]:
        """Relevance of each document to the query in [0, 1], in the same order as `documents`."""
        query_ids = self._tokenizer.encode(query, add_special_tokens=False).ids
        pairs = [
            build_pair(query_ids, encoding.ids, self._max_tokens)
            for encoding in self._tokenizer.encode_batch(documents, add_special_tokens=False)
        ]
        scores = [0.0] * len(pairs)
        # Pairs of similar length go together so that little time is spent on padding.
        order = sorted(range(len(pairs)), key=lambda index: len(pairs[index]))
        for start in range(0, len(order), self._batch_size):
            batch = order[start:start + self._batch_size]
            width = max(len(pairs[index]) for index in batch)
            input_ids = np.full((len(batch), width), PAD_ID, dtype=np.int64)
            attention_mask = np.zeros((len(batch), width), dtype=np.int64)
            for row, index in enumerate(batch):
                input_ids[row, :len(pairs[index])] = pairs[index]
                attention_mask[row, :len(pairs[index])] = 1
            feed = {"input_ids": input_ids, "attention_mask": attention_mask}
            if "token_type_ids" in self._input_names:
                feed["token_type_ids"] = np.zeros_like(input_ids)
            logits = self._session.run(None, {k: v for k, v in feed.items() if k in self._input_names})[0]
            for index, value in zip(batch, sigmoid(logits.reshape(len(batch), -1)[:, 0])):
                scores[index] = float(value)
        return scores
