"""咨询HTTP、会话隔离、失败不提交和推荐接口兼容测试，不调用真实模型。"""

import json
import threading
import time
import unittest
from types import SimpleNamespace
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from ai_server import AiServer, Conversations
from tools.backend import ToolError


class FakeAssistant:
    def __init__(self, fail=False):
        self._history, self._sources, self._next_source = [], [], 1
        self.fail = fail

    @property
    def history(self):
        return self._history

    @property
    def sources(self):
        return self._sources

    def stream(self, question):
        yield "查询结果"
        if self.fail:
            raise RuntimeError("invalid citation")
        yield "[T1]"
        self._history += [{"role": "user", "content": question}, {"role": "assistant", "content": "查询结果[T1]"}]
        self._sources = [{"source_id": "T1", "tool": "search_books", "data": {"total": 1}}]
        self._next_source += 1


class ConversationTests(unittest.TestCase):
    def test_owner_isolation_and_invalid_id(self):
        store = Conversations()
        key, row = store.acquire(1, create=True)
        store.release(row)
        for owner, value, status in [(2, key, 404), (1, "bad-id", 400), (True, key, 400)]:
            with self.assertRaises(ToolError) as error:
                store.acquire(owner, value)
            self.assertEqual(error.exception.status, status)

    def test_concurrent_turn_is_rejected(self):
        store = Conversations()
        key, row = store.acquire(1, create=True)
        try:
            with self.assertRaises(ToolError) as error:
                store.acquire(1, key)
            self.assertEqual(error.exception.status, 409)
        finally:
            store.release(row)

    def test_ttl_and_capacity(self):
        store = Conversations(capacity=1)
        key, row = store.acquire(1, create=True)
        store.release(row)
        with self.assertRaises(ToolError):
            store.acquire(2, create=True)
        self.assertIn(key, store.rows)
        row.touched = 0
        _, new = store.acquire(2, create=True)
        store.release(new)
        self.assertNotIn(key, store.rows)

    def test_busy_capacity_cannot_evict_running_turn(self):
        store = Conversations(capacity=1)
        _, row = store.acquire(1, create=True)
        with self.assertRaises(ToolError) as error:
            store.acquire(2, create=True)
        self.assertEqual(error.exception.status, 503)
        store.release(row)


class AssistantHttpTests(unittest.TestCase):
    def setUp(self):
        self.token = "internal-test-token-more-than-24-characters"
        self.fail = False
        self.server = AiServer(("127.0.0.1", 0), self.token,
                               lambda token, knowledge: (FakeAssistant(self.fail), SimpleNamespace(current_token="rotated-business-token")))
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()

    def call(self, data=None, path="/internal/assistant/chat", method="POST", owner=1, secret=None):
        value = {"memberId": owner, "question": "查询Java书"} if data is None and method == "POST" else data
        raw = json.dumps(value).encode() if value is not None else None
        request = Request(f"http://127.0.0.1:{self.server.server_port}" + path, data=raw, method=method,
                          headers={"Authorization": "Bearer " + (secret or self.token),
                                   "X-Library-Token": "business-secret-not-for-model", "X-Member-Id": str(owner)})
        return urlopen(request, timeout=3)

    def chat(self):
        with self.call() as response:
            self.assertEqual(response.headers["X-Refreshed-Token"], "rotated-business-token")
            text = response.read().decode()
        meta = json.loads(text.split("event: meta\ndata: ")[1].split("\n")[0])
        return meta["conversationId"], text

    def test_stream_history_and_clear(self):
        key, text = self.chat()
        self.assertIn("event: delta", text)
        self.assertIn("event: sources", text)
        self.assertIn("event: done", text)
        self.assertNotIn("business-secret", text)
        path = "/internal/assistant/conversations/" + key
        with self.call(path=path, method="GET") as response:
            self.assertEqual(len(json.load(response)["messages"]), 2)
        with self.call({"memberId": 1, "conversationId": key, "question": "还有呢"}) as response:
            self.assertIn(b"event: done", response.read())
        self.assertEqual(len(self.server.conversations.rows[key].history), 4)
        with self.call(path=path, method="DELETE") as response:
            self.assertTrue(json.load(response)["deleted"])
        with self.assertRaises(HTTPError) as error:
            self.call(path=path, method="GET")
        self.assertEqual(error.exception.code, 404)
        error.exception.close()

    def test_cross_member_read_delete_and_continue_rejected(self):
        key, _ = self.chat()
        path = "/internal/assistant/conversations/" + key
        for method in ("GET", "DELETE", "POST"):
            with self.assertRaises(HTTPError) as error:
                self.call({"memberId": 2, "conversationId": key, "question": "查询"} if method == "POST" else None,
                          path="/internal/assistant/chat" if method == "POST" else path, method=method, owner=2)
            self.assertEqual(error.exception.code, 404)
            error.exception.close()

    def test_invalid_question_and_extra_identity_rejected(self):
        for value in [{"memberId": 1, "question": " "}, {"memberId": 1, "question": "x" * 2001},
                      {"memberId": 1, "question": "test", "url": "http://evil"}]:
            with self.assertRaises(HTTPError) as error:
                self.call(value)
            self.assertEqual(error.exception.code, 400)
            error.exception.close()

    def test_internal_auth(self):
        with self.assertRaises(HTTPError) as error:
            self.call(secret="wrong")
        self.assertEqual(error.exception.code, 401)
        error.exception.close()

    def test_model_busy_is_503(self):
        self.server.slot.acquire()
        try:
            with self.assertRaises(HTTPError) as error:
                self.call()
            self.assertEqual(error.exception.code, 503)
            error.exception.close()
        finally:
            self.server.slot.release()

    def test_stream_failure_never_commits_partial_history(self):
        self.fail = True
        key, text = self.chat()
        self.assertIn("event: error", text)
        self.assertNotIn("event: done", text)
        self.assertEqual(self.server.conversations.rows[key].history, [])
        self.assertFalse(self.server.slot._value == 0)


if __name__ == "__main__":
    unittest.main()
