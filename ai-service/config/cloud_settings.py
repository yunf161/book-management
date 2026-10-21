"""阿里云百炼配置；密钥不进入repr、提示词或前端响应。"""

import math
import os
from dataclasses import dataclass, field
from urllib.parse import urlsplit


@dataclass(frozen=True)
class CloudSettings:
    api_key: str = field(default="", repr=False)
    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    model: str = "qwen-plus"
    timeout: float = 15

    def __post_init__(self):
        url = urlsplit(self.base_url)
        # 只允许官方HTTPS端点，避免配置失误把业务内容和API Key发往其他服务器。
        if (url.scheme != "https" or not url.hostname
                or not url.hostname.endswith(".aliyuncs.com")
                or url.username or url.password or url.query or url.fragment
                or url.port not in (None, 443)
                or url.path.rstrip("/") != "/compatible-mode/v1"):
            raise ValueError("百炼地址必须是官方HTTPS兼容接口地址")
        if any(character in self.api_key for character in "\r\n"):
            raise ValueError("百炼密钥格式错误")
        if not self.model.strip() or len(self.model) > 150:
            raise ValueError("百炼模型名称格式错误")
        if not math.isfinite(self.timeout) or not 0 < self.timeout <= 30:
            raise ValueError("百炼超时必须在0～30秒之间")

    @classmethod
    def from_env(cls):
        return cls(
            api_key=os.getenv("DASHSCOPE_API_KEY", "").strip(),
            base_url=os.getenv("DASHSCOPE_BASE_URL", cls.base_url),
            model=os.getenv("DASHSCOPE_MODEL", cls.model),
            timeout=float(os.getenv("DASHSCOPE_TIMEOUT", cls.timeout)),
        )
