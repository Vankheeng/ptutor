"""Dense embedding with bge-m3 (ONNX Runtime, int8)."""

from pathlib import Path

import numpy as np

from .config import EMBED_DIMENSIONS, EMBED_MODEL_FILE, EMBED_MODEL_VERSION

# XLM-RoBERTa vocabulary used by bge-m3.
PAD_TOKEN = "<pad>"
PAD_ID = 1


def pool_and_normalize(last_hidden_state: np.ndarray) -> np.ndarray:
    """bge-m3 dense vector: hidden state of the first token (CLS), scaled to unit length."""
    cls = last_hidden_state[:, 0, :].astype(np.float32)
    norms = np.linalg.norm(cls, axis=1, keepdims=True)
    return cls / np.maximum(norms, 1e-12)


class Embedder:
    model_version = EMBED_MODEL_VERSION
    dimensions = EMBED_DIMENSIONS

    def __init__(self, embed_dir: Path, max_tokens: int = 512, threads: int = 0):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        self._tokenizer = Tokenizer.from_file(str(embed_dir / "tokenizer.json"))
        self._tokenizer.enable_truncation(max_length=max_tokens)
        self._tokenizer.enable_padding(pad_id=PAD_ID, pad_token=PAD_TOKEN)

        options = ort.SessionOptions()
        if threads > 0:
            options.intra_op_num_threads = threads
        self._session = ort.InferenceSession(
            str(embed_dir / EMBED_MODEL_FILE), sess_options=options, providers=["CPUExecutionProvider"]
        )
        self._input_names = {i.name for i in self._session.get_inputs()}

    def embed(self, texts: list[str]) -> np.ndarray:
        """One unit-length vector of 1024 floats per text, in the same order."""
        encodings = self._tokenizer.encode_batch(texts)
        feed = {
            "input_ids": np.array([e.ids for e in encodings], dtype=np.int64),
            "attention_mask": np.array([e.attention_mask for e in encodings], dtype=np.int64),
        }
        if "token_type_ids" in self._input_names:
            feed["token_type_ids"] = np.zeros_like(feed["input_ids"])
        outputs = self._session.run(None, {k: v for k, v in feed.items() if k in self._input_names})
        vectors = pool_and_normalize(outputs[0])
        if vectors.shape[1] != self.dimensions:
            raise RuntimeError(f"Model returned {vectors.shape[1]} dimensions, expected {self.dimensions}")
        return vectors
