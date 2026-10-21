"""Java工具连接配置，令牌仅留在HTTP适配器内。"""

import math
import os
from dataclasses import dataclass
from urllib.parse import urlsplit


@dataclass(frozen=True)
class BackendSettings:
    base_url: str = "http://127.0.0.1:8080"
    timeout: float = 15

    def __post_init__(self):
        url = urlsplit(self.base_url)
        if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("Java服务地址格式错误")
        if url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1"):
            raise ValueError("非本机Java服务必须使用HTTPS")
        if not math.isfinite(self.timeout) or self.timeout <= 0:
            raise ValueError("Java超时必须为正数")

    @classmethod
    def from_env(cls):
        return cls(os.getenv("LIBRARY_API_URL", cls.base_url), float(os.getenv("LIBRARY_API_TIMEOUT", cls.timeout)))
