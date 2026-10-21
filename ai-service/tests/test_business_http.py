"""Python内部HTTP业务流：只传草稿，咨询与业务对话不能混用。"""
import json
import threading
import unittest
from types import SimpleNamespace
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from ai_server import AiServer
from tests.test_stage7 import FakeAssistant


class BusinessHttpTests(unittest.TestCase):
    def setUp(self):
        self.secret = "business-http-test-internal-token"
        def factory(token, knowledge):
            assistant = FakeAssistant()
            assistant.proposal = {"skill": "plan_create", "arguments": {"name": "测试", "targetBooks": 3}}
            return assistant, SimpleNamespace(current_token="user-token")
        self.server = AiServer(("127.0.0.1", 0), self.secret, assistant_factory=factory, business_factory=factory)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()

    def request(self, data):
        raw = data if isinstance(data, bytes) else json.dumps(data).encode()
        return urlopen(Request(f"http://127.0.0.1:{self.server.server_port}/internal/assistant/chat", raw,
            {"Authorization": "Bearer " + self.secret, "X-Library-Token": "user-token"}), timeout=3)

    def test_business_sends_proposal_not_execution(self):
        with self.request({"memberId": 1, "question": "创建计划", "task": "BUSINESS"}) as response:
            text = response.read().decode()
        self.assertIn("event: proposal", text)
        self.assertIn("event: done", text)
        self.assertNotIn("event: action", text)

    def test_consultation_never_sends_proposal_and_cannot_switch_task(self):
        with self.request({"memberId": 1, "question": "查询"}) as response:
            text = response.read().decode()
        self.assertNotIn("event: proposal", text)
        key = json.loads(text.split("event: meta\ndata: ")[1].split("\n")[0])["conversationId"]
        with self.assertRaises(HTTPError) as error:
            self.request({"memberId": 1, "question": "创建", "task": "BUSINESS", "conversationId": key})
        self.assertEqual(error.exception.code, 409)
        self.assertFalse(self.server.conversations.rows[key].lock.locked())

    def test_malformed_json_is_bad_request_and_releases_capacity(self):
        with self.assertRaises(HTTPError) as error:
            self.request(b"{invalid")
        self.assertEqual(error.exception.code, 400)
        with self.request({"memberId": 1, "question": "测试"}) as response:
            self.assertEqual(response.status, 200)
