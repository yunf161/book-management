"""阶段1测试：模拟 Ollama 协议和会话边界，不依赖模型服务。"""

import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from clients.ollama_client import OllamaClient, OllamaError
from config.settings import Settings


def response(*chunks):
    return io.BytesIO(b"\n".join(json.dumps(chunk, ensure_ascii=False).encode() for chunk in chunks))


class ClientTests(unittest.TestCase):
    def call(self, source):
        with patch("clients.ollama_client.urlopen", return_value=source):
            return list(OllamaClient(Settings()).stream_chat([{"role": "user", "content": "你好"}]))

    def test_stream_and_payload(self):
        with patch("clients.ollama_client.urlopen", return_value=response(
            {"message": {"content": "你"}, "done": False},
            {"message": {"content": "好"}, "done": True},
        )) as opened:
            self.assertEqual(list(OllamaClient(Settings()).stream_chat([])), ["你", "好"])
            payload = json.loads(opened.call_args.args[0].data)
            self.assertEqual(payload["options"]["num_ctx"], 32768)
            self.assertFalse(payload["think"])
            self.assertEqual(opened.call_args.kwargs["timeout"], 180)

    def test_service_error(self):
        with self.assertRaisesRegex(OllamaError, "返回错误"):
            self.call(response({"error": "model failed"}))

    def test_truncated_stream(self):
        with self.assertRaisesRegex(OllamaError, "提前中断"):
            self.call(response({"message": {"content": "部分文本"}}))

    def test_invalid_json(self):
        with self.assertRaisesRegex(OllamaError, "格式错误"):
            self.call(io.BytesIO(b"not json"))

    def test_invalid_message(self):
        with self.assertRaisesRegex(OllamaError, "格式错误"):
            self.call(response({"message": None}))

    def test_output_limit(self):
        with self.assertRaisesRegex(OllamaError, "输出上限"):
            self.call(response({"done": True, "done_reason": "length"}))

    def test_http_error(self):
        with patch("clients.ollama_client.urlopen", side_effect=HTTPError("url", 404, "missing", {}, None)):
            with self.assertRaisesRegex(OllamaError, "HTTP 404"):
                list(OllamaClient(Settings()).stream_chat([]))

    def test_connection_error(self):
        with patch("clients.ollama_client.urlopen", side_effect=URLError("refused")):
            with self.assertRaisesRegex(OllamaError, "无法连接"):
                list(OllamaClient(Settings()).stream_chat([]))

    def test_timeout(self):
        with patch("clients.ollama_client.urlopen", side_effect=TimeoutError()):
            with self.assertRaisesRegex(OllamaError, "超时"):
                list(OllamaClient(Settings()).stream_chat([]))

    def test_settings_validation(self):
        for change in ({"context": 0}, {"model": " "}, {"timeout": float("nan")},
                       {"base_url": "file:///tmp"}, {"max_turns": 0}, {"max_tokens": 32768}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                Settings(**change)

    def test_environment(self):
        with patch.dict("os.environ", {"OLLAMA_MODEL": "example", "OLLAMA_CONTEXT_LENGTH": "8192"}):
            self.assertEqual(Settings.from_env().model, "example")
            self.assertEqual(Settings.from_env().context, 8192)



if __name__ == "__main__":
    unittest.main()
