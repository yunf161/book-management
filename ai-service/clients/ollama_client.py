"""Ollama 原生流式客户端，仅依赖 Python 标准库。"""

import json
import socket
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from config.settings import Settings
from clients.usage import record


class OllamaError(RuntimeError):
    """向调用方提供可理解的推理服务错误。"""


class OllamaClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def stream_chat(self, messages):
        """逐段返回文本；必须收到 done，提前断流视为失败，不自动重试。"""
        payload = {
            "model": self.settings.model,
            "messages": messages,
            "stream": True,
            "think": False,
            "options": {
                "temperature": self.settings.temperature,
                "num_ctx": self.settings.context,
                "num_predict": self.settings.max_tokens,
            },
        }
        # 仅显式配置时限制设备层数/线程；默认保持服务端设备选择。
        if self.settings.num_gpu is not None:
            payload["options"]["num_gpu"] = self.settings.num_gpu
        if self.settings.num_thread is not None:
            payload["options"]["num_thread"] = self.settings.num_thread
        request = Request(
            self.settings.base_url.rstrip("/") + "/api/chat",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            # timeout 限制连接及单次阻塞读取；不是整轮回答的总时限。
            with urlopen(request, timeout=self.settings.timeout) as response:
                for line in response:
                    if not line.strip():
                        continue
                    try:
                        chunk = json.loads(line)
                        if not isinstance(chunk, dict):
                            raise ValueError("响应不是对象")
                        if chunk.get("error"):
                            raise OllamaError("Ollama 返回错误：" + str(chunk["error"]))
                        message = chunk.get("message", {})
                        content = message.get("content", "")
                        if not isinstance(content, str):
                            raise ValueError("content 不是字符串")
                    except (ValueError, AttributeError, UnicodeError) as exc:
                        raise OllamaError("Ollama 响应格式错误") from exc
                    if content:
                        yield content
                    if chunk.get("done") is True:
                        record(self, "ollama", self.settings.model, chunk)
                        if chunk.get("done_reason") == "length":
                            raise OllamaError("回答达到输出上限，未保存本轮；请增大 --max-tokens")
                        return
                raise OllamaError("Ollama 响应提前中断，未收到完成标记")
        except HTTPError as exc:
            detail = "模型可能未下载，请检查 ollama list" if exc.code == 404 else "请检查 Ollama 服务日志"
            raise OllamaError(f"Ollama HTTP {exc.code}：{detail}") from exc
        except (TimeoutError, socket.timeout) as exc:
            raise OllamaError("Ollama 调用超时，请检查模型负载或增大 --timeout") from exc
        except URLError as exc:
            raise OllamaError("无法连接 Ollama，请确认服务地址、端口和运行状态") from exc
        except OSError as exc:
            raise OllamaError("Ollama 连接在读取时断开") from exc
