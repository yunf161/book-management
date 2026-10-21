"""保留现用模型客户端的采样参数测试，不再测试已删除的离线任务入口。"""

import unittest
from unittest.mock import patch
from config.settings import Settings


class SamplingTests(unittest.TestCase):
    def test_temperature_validation(self):
        for value in (-1, 3, float("nan")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                Settings(temperature=value)
        self.assertEqual(Settings().temperature, 0.2)

    def test_temperature_payload(self):
        import io
        import json
        from clients.ollama_client import OllamaClient
        with patch("clients.ollama_client.urlopen", return_value=io.BytesIO(b'{"done":true}')) as opened:
            list(OllamaClient(Settings(temperature=0.1)).stream_chat([]))
            self.assertEqual(json.loads(opened.call_args.args[0].data)["options"]["temperature"], 0.1)


if __name__ == "__main__":
    unittest.main()
