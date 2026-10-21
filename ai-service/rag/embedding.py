"""Ollama 嵌入服务：CPU运行、显式传向量，不使用Chroma默认自动下载模型。"""

import json
import math
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from clients.ollama_client import OllamaError


class OllamaEmbedding:
    def __init__(self, base_url, model, timeout=180):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout
        self._identity = None

    def _request(self, endpoint, payload=None):
        request = Request(
            self.base_url + endpoint,
            data=None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
        )
        try:
            with urlopen(request, timeout=self.timeout) as response:
                result = json.load(response)
            if not isinstance(result, dict):
                raise ValueError("响应不是对象")
            if result.get("error"):
                raise OllamaError("嵌入服务错误：" + str(result["error"]))
            return result
        except HTTPError as exc:
            raise OllamaError(f"嵌入服务HTTP {exc.code}，请确认已下载 {self.model}") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise OllamaError("嵌入服务连接失败或超时，请检查Ollama") from exc
        except (ValueError, UnicodeError) as exc:
            raise OllamaError("嵌入服务响应格式错误") from exc

    @property
    def identity(self):
        # 将模型digest加入集合标识，避免同名模型更新后混用不同向量空间。
        if self._identity is None:
            models = self._request("/api/tags").get("models", [])
            match = next((m for m in models if m.get("name") == self.model), None)
            if not match or not match.get("digest"):
                raise OllamaError(f"未找到本地嵌入模型 {self.model}，请先下载")
            self._identity = self.model + ":" + match["digest"] + ":query-instruct-v1"
        return self._identity

    def embed(self, texts, query=False):
        if not texts or any(not isinstance(t, str) or not t.strip() for t in texts):
            raise ValueError("嵌入输入不能为空")
        inputs = ["Instruct: Given a library question, retrieve relevant policy or knowledge passages.\nQuery: " + t for t in texts] if query else texts
        result = self._request("/api/embed", {
            "model": self.model,
            "input": inputs,
            "truncate": False,
            "keep_alive": "5m",
            "options": {"num_gpu": 0, "num_ctx": 4096},
        })
        vectors = result.get("embeddings")
        if not isinstance(vectors, list) or len(vectors) != len(texts):
            raise OllamaError("嵌入数量与输入数量不一致")
        dimensions = set()
        for vector in vectors:
            if not isinstance(vector, list) or not vector:
                raise OllamaError("嵌入向量为空")
            if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in vector):
                raise OllamaError("嵌入向量含非法数值")
            if sum(v * v for v in vector) == 0:
                raise OllamaError("嵌入向量不能为零")
            dimensions.add(len(vector))
        if len(dimensions) != 1:
            raise OllamaError("嵌入向量维度不一致")
        return vectors
