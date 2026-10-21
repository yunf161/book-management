"""云端适配、工具协议转换和真实HTTP降级；不使用付费API或真实密钥。"""

import io
import json
import threading
import unittest
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from types import SimpleNamespace
from unittest.mock import Mock, patch
from urllib.request import Request
from urllib.error import HTTPError, URLError

from clients.aliyun_client import AliyunClient, cloud_messages
from clients.model_client import FallbackModelClient, create_model_client
from clients.ollama_client import OllamaError
from clients.tool_client import ToolOllamaClient
from config.cloud_settings import CloudSettings
from config.settings import Settings
from recommendation_rerank import rerank
from tests.test_stage6 import sample, answer


def completion(content="云端回答", calls=None, reason="stop"):
    return {"choices": [{"finish_reason": reason, "message": {
        "role": "assistant", "content": content, "tool_calls": calls,
    }}]}


class CloudConfigTests(unittest.TestCase):
    def test_key_is_not_in_repr_and_env_is_loaded(self):
        with patch.dict("os.environ", {
            "DASHSCOPE_API_KEY": "test-only-secret",
            "DASHSCOPE_MODEL": "qwen-plus",
            "DASHSCOPE_BASE_URL": "https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
            "DASHSCOPE_TIMEOUT": "4",
        }):
            settings = CloudSettings.from_env()
        self.assertNotIn("test-only-secret", repr(settings))
        self.assertEqual(settings.timeout, 4)
        self.assertEqual(settings.model, "qwen-plus")

    def test_untrusted_endpoints_and_invalid_config_are_rejected(self):
        for values in (
            {"base_url": "http://dashscope.aliyuncs.com/compatible-mode/v1"},
            {"base_url": "https://dashscope.aliyuncs.com.evil.test/compatible-mode/v1"},
            {"base_url": "https://evil.test/compatible-mode/v1"},
            {"base_url": "https://user:secret@dashscope.aliyuncs.com/compatible-mode/v1"},
            {"api_key": "invalid\r\nheader"}, {"timeout": 0}, {"timeout": float("nan")},
        ):
            with self.subTest(values=values), self.assertRaises(ValueError):
                CloudSettings(**values)

    def test_no_key_or_bad_cloud_configuration_keeps_local(self):
        for env in ({"DASHSCOPE_API_KEY": ""}, {"DASHSCOPE_API_KEY": "test", "DASHSCOPE_TIMEOUT": "invalid"}):
            with patch.dict("os.environ", env):
                client = create_model_client(Settings(base_url="http://127.0.0.1:11435"))
            self.assertIsNone(client.cloud)
            self.assertEqual(client.local.settings.base_url, "http://127.0.0.1:11435")


class CloudProtocolTests(unittest.TestCase):
    def client(self, result):
        client = AliyunClient(Settings(), CloudSettings(api_key="test-only-key"))
        client._open = Mock(return_value=io.BytesIO(json.dumps(result).encode()))
        return client

    def test_tool_results_get_matching_ids_without_mutating_source(self):
        messages = [
            {"role": "user", "content": "查询"},
            {"role": "assistant", "content": "", "tool_calls": [
                {"function": {"name": "books", "arguments": {"id": 1}}},
                {"function": {"name": "books", "arguments": {"id": 2}}},
            ]},
            {"role": "tool", "tool_name": "books", "content": "first"},
            {"role": "tool", "tool_name": "books", "content": "second"},
        ]
        original = deepcopy(messages)
        converted = cloud_messages(messages)
        self.assertEqual(messages, original)
        calls = converted[1]["tool_calls"]
        self.assertNotEqual(calls[0]["id"], calls[1]["id"])
        self.assertEqual(converted[2]["tool_call_id"], calls[0]["id"])
        self.assertEqual(converted[3]["tool_call_id"], calls[1]["id"])
        self.assertEqual(json.loads(calls[0]["function"]["arguments"]), {"id": 1})

    def test_orphan_tool_message_is_rejected(self):
        with self.assertRaises(ValueError):
            cloud_messages([{"role": "tool", "tool_name": "books", "content": "x"}])

    def test_cloud_tool_call_is_converted_to_existing_contract(self):
        calls = [{"id": "remote-id", "type": "function", "function": {
            "name": "books", "arguments": '{"id":1}',
        }}]
        client = self.client(completion(None, calls, "tool_calls"))
        result = client.plan_tools([], [{"type": "function", "function": {"name": "books"}}])
        self.assertEqual(result["tool_calls"], [{"function": {"name": "books", "arguments": {"id": 1}}}])
        payload = json.loads(client._open.call_args.args[0].data)
        self.assertFalse(payload["enable_thinking"])
        self.assertFalse(payload["stream"])
        self.assertNotIn("options", payload)

    def test_truncated_empty_and_invalid_responses_fail(self):
        for result in (completion(reason="length"), completion(""), {}, {"choices": None}):
            with self.subTest(result=result), self.assertRaises(OllamaError):
                list(self.client(result).stream_chat([]))

    def test_tool_argument_and_unopened_tool_rejected(self):
        for function in (
            {"name": "books", "arguments": "not-json"},
            {"name": "books", "arguments": "[]"},
            {"name": "delete_member", "arguments": "{}"},
        ):
            client = self.client(completion(None, [{"function": function}], "tool_calls"))
            with self.assertRaises(OllamaError):
                client.plan_tools([], [{"function": {"name": "books"}}])

    def test_oversized_response_is_rejected(self):
        client = self.client({})
        client._open.return_value = io.BytesIO(b" " * 1_000_001)
        with self.assertRaises(OllamaError):
            list(client.stream_chat([]))

    def test_credit_error_logs_only_approved_code(self):
        client = self.client({})
        body = io.BytesIO(json.dumps({"error": {"code": "Arrearage", "message": "private request content"}}).encode())
        client._open.side_effect = HTTPError("https://dashscope.aliyuncs.com", 400, "bad", {}, body)
        with self.assertRaises(OllamaError) as caught:
            list(client.stream_chat([]))
        self.assertIn("400:Arrearage", str(caught.exception))
        self.assertNotIn("private request content", str(caught.exception))

    def test_timeout_and_connection_failure_fall_back(self):
        for failure in (TimeoutError(), URLError("test connection failure")):
            cloud = self.client({})
            cloud._open.side_effect = failure
            local = Mock(settings=Settings())
            local.stream_chat.return_value = iter(["本地成功"])
            with self.subTest(failure=type(failure).__name__):
                self.assertEqual(list(FallbackModelClient(local, cloud).stream_chat([])), ["本地成功"])

    def test_invalid_required_parameters_fail_before_tool_execution(self):
        client = self.client(completion(None, [{"function": {"name": "books", "arguments": "{}"}}], "tool_calls"))
        tool = {"function": {"name": "books", "parameters": {
            "type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"],
        }}}
        with self.assertRaises(OllamaError):
            client.plan_tools([], [tool])

    def test_partial_cloud_output_is_never_mixed_with_local(self):
        def partial(_):
            yield "不能泄漏的半截回答"
            raise OllamaError("interrupted")
        local = SimpleNamespace(settings=Settings(), stream_chat=lambda _: iter(["本地", "回答"]))
        cloud = SimpleNamespace(stream_chat=partial)
        self.assertEqual(list(FallbackModelClient(local, cloud).stream_chat([])), ["本地", "回答"])

    def test_both_failures_propagate_local_error_once(self):
        cloud, local = Mock(), Mock(settings=Settings())
        cloud.plan_tools.side_effect = OllamaError("cloud")
        local.plan_tools.side_effect = OllamaError("local")
        with self.assertRaisesRegex(OllamaError, "local"):
            FallbackModelClient(local, cloud).plan_tools([], [])
        self.assertEqual(cloud.plan_tools.call_count, 1)
        self.assertEqual(local.plan_tools.call_count, 1)

    def test_recommendation_accepts_cloud_and_invalid_cloud_falls_back(self):
        cloud = Mock()
        with patch("recommendation_rerank.create_cloud_client", return_value=cloud), patch("recommendation_rerank.urlopen") as local:
            cloud.json_chat.return_value = answer()
            self.assertEqual(rerank(sample()), answer())
            local.assert_not_called()
            cloud.json_chat.return_value = {"items": [{"bookId": -1, "reason": "越界"}]}
            local.return_value = io.BytesIO(json.dumps({"done": True, "message": {"content": json.dumps(answer())}}).encode())
            self.assertEqual(rerank(sample()), answer())
            self.assertEqual(local.call_count, 1)


class CloudHttpTests(unittest.TestCase):
    def setUp(self):
        self.mode, self.requests = "success", []
        case = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_POST(self):
                value = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                case.requests.append((self.path, value, self.headers.get("Authorization")))
                status, data = 200, completion()
                if self.path == "/cloud":
                    if case.mode in ("401", "429", "503"):
                        status, data = int(case.mode), {"error": "test-only-key should never be logged"}
                    elif case.mode == "redirect":
                        self.send_response(302)
                        self.send_header("Location", "/steal")
                        self.send_header("Content-Length", "0")
                        self.end_headers()
                        return
                    elif case.mode == "invalid":
                        data = {"choices": None}
                else:
                    data = {"done": True, "message": {"content": "本地HTTP回答", "tool_calls": []}}
                raw = json.dumps(data, ensure_ascii=False).encode()
                self.send_response(status)
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        self.base = "http://127.0.0.1:" + str(self.server.server_port)
        settings = Settings(base_url=self.base, model="qwen3.5:4b", timeout=3)
        cloud = AliyunClient(settings, CloudSettings(api_key="test-only-key", timeout=1))
        opener = cloud._open

        # 仅测试传输层映射到本机；生产配置不开放任意HTTP端点。
        def local_transport(request, timeout):
            mapped = Request(self.base + "/cloud", request.data, dict(request.header_items()), method="POST")
            return opener(mapped, timeout=timeout)

        cloud._open = local_transport
        self.client = FallbackModelClient(ToolOllamaClient(settings), cloud)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()

    def test_success_only_calls_cloud(self):
        self.assertEqual(list(self.client.stream_chat([])), ["云端回答"])
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(self.requests[0][1]["model"], "qwen-plus")

    def test_http_errors_use_local_without_forwarding_cloud_key(self):
        for mode in ("401", "429", "503", "invalid", "redirect"):
            self.mode, self.requests = mode, []
            with self.subTest(mode=mode):
                self.assertEqual(list(self.client.stream_chat([])), ["本地HTTP回答"])
                self.assertEqual([row[0] for row in self.requests], ["/cloud", "/api/chat"])
                self.assertIsNone(self.requests[1][2])

    def test_tool_planning_http_failure_uses_local(self):
        self.mode = "503"
        self.assertEqual(self.client.plan_tools([], [])["tool_calls"], [])
        self.assertEqual([row[0] for row in self.requests], ["/cloud", "/api/chat"])
        self.assertFalse(self.requests[1][1]["stream"])
