"""候选重排协议、降级输入边界及内部HTTP鉴权/并发测试，不需要真实模型。"""

import copy
import io
import json
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from recommendation_rerank import rerank, validate_input, validate_result
from rerank_server import RerankServer


def sample():
    return {"interests": ["Java"], "candidates": [
        {"bookId": n, "categoryId": (n - 1) // 2 + 1, "title": "书" + str(n),
         "author": "作者", "description": "公开简介", "tags": ["Java"], "ruleScore": 20}
        for n in range(1, 9)
    ]}


def answer():
    return {"items": [{"bookId": n, "reason": "符合Java兴趣"} for n in range(6, 0, -1)]}


class RerankTests(unittest.TestCase):
    def setUp(self):
        # 本组验证本地传输协议；机器配置了云密钥也不能意外请求真实云服务。
        cloud = patch("recommendation_rerank.create_cloud_client", return_value=None)
        cloud.start()
        self.addCleanup(cloud.stop)

    def test_valid_input_and_order(self):
        self.assertEqual(validate_input(sample()), sample())
        self.assertEqual(validate_result(answer(), sample()["candidates"]), answer())

    def test_private_fields_rejected(self):
        value = sample()
        value["memberId"] = 10
        with self.assertRaises(ValueError):
            validate_input(value)

    def test_duplicate_candidates(self):
        value = sample()
        value["candidates"][1] = value["candidates"][0]
        with self.assertRaises(ValueError):
            validate_input(value)

    def test_invalid_input_values(self):
        for field, value in [("bookId", True), ("ruleScore", float("nan")), ("description", "a" * 501), ("tags", "Java")]:
            with self.subTest(field=field):
                data = sample()
                data["candidates"][0][field] = value
                with self.assertRaises(ValueError):
                    validate_input(data)

    def test_candidate_limit(self):
        for rows in [[], sample()["candidates"] * 3]:
            with self.assertRaises(ValueError):
                validate_input({"interests": [], "candidates": rows})

    def test_unknown_duplicate_and_boolean_ids(self):
        for book_id in [999, 5, True]:
            value = answer()
            value["items"][0]["bookId"] = book_id
            with self.assertRaises(ValueError):
                validate_result(value, sample()["candidates"])

    def test_count_reason_and_score_rejected(self):
        bad = []
        value = answer()
        value["items"].pop()
        bad.append(value)
        for reason in [" ", "x" * 301, None]:
            value = answer()
            value["items"][0]["reason"] = reason
            bad.append(value)
        value = answer()
        value["items"][0]["score"] = 100
        bad.append(value)
        for value in bad:
            with self.assertRaises(ValueError):
                validate_result(value, sample()["candidates"])

    def test_diversity_rejected(self):
        candidates = sample()["candidates"]
        for i in range(3):
            candidates[i]["categoryId"] = 99
        with self.assertRaises(ValueError):
            validate_result(answer(), candidates)

    def test_sparse_categories_relax_only_after_covering(self):
        candidates = sample()["candidates"]
        for row in candidates:
            row["categoryId"] = 1
        candidates[-1]["categoryId"] = 2
        with self.assertRaises(ValueError):
            validate_result(answer(), candidates)
        value = answer()
        value["items"][0]["bookId"] = 8
        self.assertEqual(len(validate_result(value, candidates)["items"]), 6)

    def test_fewer_than_six(self):
        value = answer()
        value["items"] = [{"bookId": 1, "reason": "符合兴趣"}]
        self.assertEqual(validate_result(value, sample()["candidates"][:1]), value)

    def test_interest_coverage_cannot_be_ignored(self):
        candidates = sample()["candidates"]
        for row in candidates:
            row["tags"] = ["文学"]
        candidates[-1]["tags"] = ["Java"]
        with self.assertRaises(ValueError):
            validate_result(answer(), candidates, ["Java"])

    @patch("recommendation_rerank.urlopen")
    def test_structured_schema_and_no_personal_data(self, open_mock):
        open_mock.return_value = io.BytesIO(json.dumps({
            "done": True, "message": {"content": json.dumps(answer())}
        }).encode())
        self.assertEqual(rerank(sample()), answer())
        payload = json.loads(open_mock.call_args.args[0].data)
        self.assertFalse(payload["stream"])
        self.assertFalse(payload["think"])
        self.assertEqual(payload["options"]["num_ctx"], 32768)
        self.assertEqual(payload["format"]["properties"]["items"]["maxItems"], 6)
        self.assertNotIn("tools", payload)
        self.assertIn("不可信数据", payload["messages"][0]["content"])

    @patch("recommendation_rerank.urlopen")
    def test_incomplete_and_malformed_model(self, open_mock):
        for value in [{"done": False}, {"done": True, "done_reason": "length"},
                      {"done": True, "message": {"content": "not json"}}]:
            open_mock.return_value = io.BytesIO(json.dumps(value).encode())
            with self.assertRaises(ValueError):
                rerank(sample())


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.token = "unit-test-internal-token-at-least-24"
        self.server = RerankServer(("127.0.0.1", 0), self.token, lambda _: answer())
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def call(self, value=None, token=None, path="/internal/recommendations/rerank"):
        request = Request(f"http://127.0.0.1:{self.server.server_port}" + path,
                          data=json.dumps(sample() if value is None else value).encode(),
                          headers={"Authorization": "Bearer " + (self.token if token is None else token)}, method="POST")
        return urlopen(request, timeout=3)

    def test_http_success(self):
        with self.call() as response:
            self.assertEqual(json.load(response), answer())

    def test_auth_path_and_body(self):
        for options, status in [({"token": "wrong"}, 401), ({"path": "/other"}, 404), ({"value": {}}, 400)]:
            with self.assertRaises(HTTPError) as error:
                self.call(**options)
            self.assertEqual(error.exception.code, status)
            error.exception.close()

    def test_gpu_busy_returns_immediately(self):
        self.server.slot.acquire()
        try:
            with self.assertRaises(HTTPError) as error:
                self.call()
            self.assertEqual(error.exception.code, 503)
            error.exception.close()
        finally:
            self.server.slot.release()

    def test_model_failure_is_not_success(self):
        def fail(_):
            raise TimeoutError("model timeout")
        self.server.ranker = fail
        with self.assertRaises(HTTPError) as error:
            self.call()
        self.assertEqual(error.exception.code, 502)
        error.exception.close()
        self.assertTrue(self.server.slot.acquire(blocking=False))
        self.server.slot.release()


if __name__ == "__main__":
    unittest.main()
