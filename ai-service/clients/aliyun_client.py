"""百炼Chat Completions适配；统一为现有Ollama消息/工具格式。"""

from copy import deepcopy
import json
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from clients.ollama_client import OllamaError
from tools.backend import ToolError
from tools.registry import validate_arguments
from clients.usage import record


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, target):
        # 禁止Authorization随重定向流出官方端点。
        return None


def cloud_messages(messages):
    """给现有tool_name消息补充兼容协议必需的调用ID；不修改调用方历史。"""
    output, pending = [], []
    counter = 0
    for source in messages:
        role = source.get("role")
        row = {"role": role, "content": source.get("content", "")}
        if role == "assistant" and source.get("tool_calls"):
            calls = []
            for call in source["tool_calls"]:
                counter += 1
                function = deepcopy(call["function"])
                arguments = function.get("arguments", {})
                if isinstance(arguments, str):
                    arguments = json.loads(arguments)
                if not isinstance(arguments, dict):
                    raise ValueError("工具参数必须是对象")
                function["arguments"] = json.dumps(arguments, ensure_ascii=False)
                identifier = "call_" + str(counter)
                pending.append((function["name"], identifier))
                calls.append({"id": identifier, "type": "function", "function": function})
            row["tool_calls"] = calls
        elif role == "tool":
            match = next((item for item in pending if item[0] == source.get("tool_name")), None)
            if match is None:
                raise ValueError("工具结果缺少对应调用")
            row["tool_call_id"] = match[1]
            pending.remove(match)
        output.append(row)
    if pending:
        raise ValueError("工具调用缺少结果")
    return output


class AliyunClient:
    def __init__(self, settings, cloud):
        self.settings, self.cloud = settings, cloud
        self._open = build_opener(NoRedirect()).open

    def _completion(self, messages, tools=None, json_mode=False, max_tokens=None):
        try:
            payload = {
                "model": self.cloud.model,
                "messages": cloud_messages(messages),
                "stream": False,
                "enable_thinking": False,
                "temperature": self.settings.temperature,
                "max_tokens": max_tokens or self.settings.max_tokens,
            }
            if tools:
                payload["tools"] = tools
            if json_mode:
                payload["response_format"] = {"type": "json_object"}
            request = Request(
                self.cloud.base_url.rstrip("/") + "/chat/completions",
                json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                {"Authorization": "Bearer " + self.cloud.api_key,
                 "Content-Type": "application/json"}, method="POST",
            )
            with self._open(request, timeout=min(self.cloud.timeout, self.settings.timeout)) as response:
                raw = response.read(1_000_001)
            if len(raw) > 1_000_000:
                raise ValueError("响应过大")
            result = json.loads(raw)
            record(self, "aliyun", self.cloud.model, result.get("usage", {}))
            choices = result.get("choices")
            if not isinstance(choices, list) or len(choices) != 1:
                raise ValueError("响应选择错误")
            choice = choices[0]
            if choice.get("finish_reason") not in ("stop", "tool_calls"):
                raise ValueError("响应未完整完成")
            message = choice.get("message")
            if not isinstance(message, dict) or message.get("role") != "assistant":
                raise ValueError("消息格式错误")
            return message
        except HTTPError as error:
            # 只保留预先列出的错误码；不记录可能带输入内容的message/param。
            code = ""
            try:
                body = json.loads(error.read(8192))
                block = body.get("error", body)
                candidate = block.get("code")
                if candidate in {"Arrearage", "InsufficientQuota", "invalid_api_key", "InvalidApiKey",
                                 "Throttling", "rate_limit_exceeded", "InvalidParameter",
                                 "AccessDenied", "ModelNotFound", "InvalidParameterValue"}:
                    code = ":" + candidate
            except (ValueError, TypeError, AttributeError, OSError):
                pass
            error.close()
            # 不返回云端原始错误体，它可能含请求内容、密钥片段或业务数据。
            raise OllamaError(f"阿里云模型HTTP调用失败（{error.code}{code}）") from None
        except TimeoutError:
            raise OllamaError("阿里云模型调用超时") from None
        except (URLError, OSError):
            raise OllamaError("阿里云模型连接失败") from None
        except (ValueError, TypeError, KeyError, AttributeError) as error:
            # 仅记录异常类型，JSON解码异常的原始描述可能带响应片段。
            raise OllamaError(f"阿里云模型响应无效（{type(error).__name__}）") from None

    def stream_chat(self, messages):
        # 完整成功前不向前端输出云端片段，失败可安全回退，不混合两个模型的答案。
        message = self._completion(messages)
        content = message.get("content")
        if not isinstance(content, str) or not content.strip() or message.get("tool_calls"):
            raise OllamaError("阿里云模型未返回完整文本")
        yield content

    def plan_tools(self, messages, tools):
        message = self._completion(messages, tools=tools)
        calls = message.get("tool_calls")
        if calls is None:
            calls = []
        if not isinstance(calls, list) or len(calls) > 4:
            raise OllamaError("阿里云工具调用格式错误")
        normalized = []
        try:
            allowed = {tool["function"]["name"]: tool["function"] for tool in tools}
            for call in calls:
                function = call["function"]
                arguments = function["arguments"]
                if isinstance(arguments, str):
                    arguments = json.loads(arguments)
                if function["name"] not in allowed or not isinstance(arguments, dict):
                    raise ValueError("工具或参数无效")
                schema = allowed[function["name"]].get("parameters")
                if schema is not None:
                    # 生成参数无效属于模型失败，在执行任何只读工具之前即可降级。
                    # 后端401/403发生在模型适配层之外，不能通过降级绕开鉴权。
                    validate_arguments(schema, arguments)
                normalized.append({"function": {"name": function["name"], "arguments": arguments}})
        except (KeyError, TypeError, ValueError, ToolError):
            raise OllamaError("阿里云工具调用格式错误") from None
        return {"role": "assistant", "content": "", "tool_calls": normalized}

    def json_chat(self, messages, max_tokens=1200):
        message = self._completion(messages, json_mode=True, max_tokens=max_tokens)
        try:
            return json.loads(message["content"])
        except (KeyError, TypeError, ValueError):
            raise OllamaError("阿里云模型未返回有效JSON") from None
