"""集中管理模型配置；环境变量可被命令行参数覆盖。"""

import math
import os
from dataclasses import dataclass
from urllib.parse import urlsplit


@dataclass(frozen=True)
class Settings:
    base_url: str = "http://127.0.0.1:11434"
    model: str = "qwen3.5:4b"
    context: int = 32768
    timeout: float = 180
    max_tokens: int = 512
    max_turns: int = 10
    temperature: float = 0.2
    num_gpu: int | None = None
    num_thread: int | None = None

    def __post_init__(self):
        url = urlsplit(self.base_url)
        if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("Ollama 地址必须是无凭证、无查询参数的 HTTP/HTTPS 地址")
        if not math.isfinite(self.temperature) or not 0 <= self.temperature <= 2:
            raise ValueError("采样温度必须在 0～2 之间")
        if not self.model.strip():
            raise ValueError("模型名称不能为空")
        if any(value <= 0 for value in (self.context, self.max_tokens, self.max_turns)):
            raise ValueError("上下文、输出上限和保留轮数必须大于 0")
        if self.max_tokens >= self.context:
            raise ValueError("输出上限必须小于上下文长度")
        if not math.isfinite(self.timeout) or self.timeout <= 0:
            raise ValueError("超时必须是大于 0 的有限秒数")
        if self.num_gpu is not None and (type(self.num_gpu) is not int or self.num_gpu < 0):
            raise ValueError("GPU层数必须为非负整数，0表示CPU")
        if self.num_thread is not None and (type(self.num_thread) is not int or not 1 <= self.num_thread <= 128):
            raise ValueError("CPU线程数必须在1到128之间")

    @classmethod
    def from_env(cls):
        return cls(
            base_url=os.getenv("OLLAMA_BASE_URL", cls.base_url),
            model=os.getenv("OLLAMA_MODEL", cls.model),
            context=int(os.getenv("OLLAMA_CONTEXT_LENGTH", cls.context)),
            timeout=float(os.getenv("OLLAMA_TIMEOUT", cls.timeout)),
            max_tokens=int(os.getenv("OLLAMA_MAX_TOKENS", cls.max_tokens)),
            max_turns=int(os.getenv("CHAT_MAX_TURNS", cls.max_turns)),
            temperature=float(os.getenv("OLLAMA_TEMPERATURE", cls.temperature)),
            num_gpu=int(os.environ["OLLAMA_NUM_GPU"]) if os.getenv("OLLAMA_NUM_GPU") else None,
            num_thread=int(os.environ["OLLAMA_NUM_THREADS"]) if os.getenv("OLLAMA_NUM_THREADS") else None,
        )
