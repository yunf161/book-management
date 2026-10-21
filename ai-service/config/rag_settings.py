"""RAG 独立配置，默认数据路径不依赖当前工作目录。"""

import math
import os
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class RagSettings:
    data_dir: Path = field(default_factory=lambda: ROOT / "data" / "rag")
    embedding_model: str = "qwen3-embedding:0.6b"
    chunk_size: int = 700
    overlap: int = 100
    batch_size: int = 8
    top_k: int = 4
    min_similarity: float = 0.45

    def __post_init__(self):
        if not self.embedding_model.strip():
            raise ValueError("嵌入模型名称不能为空")
        if not 100 <= self.chunk_size <= 2000 or not 0 <= self.overlap < self.chunk_size:
            raise ValueError("分段长度需100～2000字符，重叠需非负且小于段长")
        if not 1 <= self.batch_size <= 32 or not 1 <= self.top_k <= 20:
            raise ValueError("批量大小需1～32，top_k需1～20")
        if not math.isfinite(self.min_similarity) or not 0 <= self.min_similarity <= 1:
            raise ValueError("相似度阈值必须在0～1之间")

    @classmethod
    def from_env(cls):
        return cls(
            data_dir=Path(os.getenv("RAG_DATA_DIR", ROOT / "data" / "rag")),
            embedding_model=os.getenv("RAG_EMBEDDING_MODEL", cls.embedding_model),
            chunk_size=int(os.getenv("RAG_CHUNK_SIZE", cls.chunk_size)),
            overlap=int(os.getenv("RAG_CHUNK_OVERLAP", cls.overlap)),
            batch_size=int(os.getenv("RAG_BATCH_SIZE", cls.batch_size)),
            top_k=int(os.getenv("RAG_TOP_K", cls.top_k)),
            min_similarity=float(os.getenv("RAG_MIN_SIMILARITY", cls.min_similarity)),
        )
