"""设备选项只在显式配置时传入；默认不会把核显误切成CPU或独显。"""
import io
import json
import unittest
from unittest.mock import patch
from clients.ollama_client import OllamaClient
from clients.tool_client import ToolOllamaClient
from config.settings import Settings


class DeviceOptionsTests(unittest.TestCase):
    def test_default_leaves_device_selection_to_isolated_server(self):
        with patch("clients.ollama_client.urlopen", return_value=io.BytesIO(b'{"done":true}')) as opened:
            list(OllamaClient(Settings()).stream_chat([]))
        options = json.loads(opened.call_args.args[0].data)["options"]
        self.assertNotIn("num_gpu", options)
        self.assertNotIn("num_thread", options)

    def test_explicit_cpu_configuration_is_transmitted_in_both_clients(self):
        settings = Settings(num_gpu=0, num_thread=4)
        for module, invoke in [("clients.ollama_client", lambda: list(OllamaClient(settings).stream_chat([]))),
                               ("clients.tool_client", lambda: ToolOllamaClient(settings).plan_tools([], []))]:
            with patch(module + ".urlopen", return_value=io.BytesIO(b'{"done":true,"message":{}}')) as opened:
                invoke()
            options = json.loads(opened.call_args.args[0].data)["options"]
            self.assertEqual(options["num_gpu"], 0)
            self.assertEqual(options["num_thread"], 4)

    def test_invalid_device_options_fail_before_network(self):
        for kwargs in [{"num_gpu": -1}, {"num_gpu": True}, {"num_thread": 0}, {"num_thread": 129}]:
            with self.assertRaises(ValueError):
                Settings(**kwargs)
