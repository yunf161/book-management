"""迁移与启动探针：不调用真实模型，也不修改现有知识库。"""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from ai_server import AiServer
from config.backend_settings import BackendSettings
from config.rag_settings import RagSettings


class MigrationTests(unittest.TestCase):
    def test_preflight_file_runs_from_another_working_directory(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, str(root / "preflight.py")], cwd=directory,
                capture_output=True, text=True, timeout=30,
                env={**os.environ, "LIBRARY_API_URL": "http://127.0.0.1:8080", "PYTHONUTF8": "1"},
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Python dependencies ready", result.stdout)

    def test_preflight_failure_has_nonzero_exit_and_preserves_diagnostic(self):
        root = Path(__file__).resolve().parents[1]
        result = subprocess.run(
            [sys.executable, str(root / "preflight.py")], cwd=root.parent,
            capture_output=True, text=True, encoding="utf-8", timeout=30,
            env={**os.environ, "LIBRARY_API_URL": "invalid", "PYTHONUTF8": "1"},
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ValueError", result.stderr)
        self.assertNotIn("Python dependencies ready", result.stdout)

    def test_defaults_follow_migrated_service_root(self):
        root = Path(__file__).resolve().parents[1]
        self.assertEqual(RagSettings().data_dir, root / "data/rag")
        self.assertTrue((root / "prompts/read_tools.md").is_file())
        self.assertEqual(BackendSettings().base_url, "http://127.0.0.1:8080")
        self.assertTrue((root / "knowledge/library-guide/manifest.json").is_file())

    def test_health_requires_internal_token_and_never_runs_model(self):
        def forbidden(*_):
            self.fail("健康检查不允许调用模型")

        token = "migration-internal-token-at-least-24"
        server = AiServer(("127.0.0.1", 0), token, assistant_factory=forbidden)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        url = f"http://127.0.0.1:{server.server_port}/internal/health"
        try:
            with self.assertRaises(HTTPError) as error:
                urlopen(url, timeout=3)
            with error.exception:
                self.assertEqual(error.exception.code, 401)
            request = Request(url, headers={"Authorization": "Bearer " + token})
            with urlopen(request, timeout=3) as response:
                body = json.load(response)
            self.assertEqual(body, {
                "service": "ai-library-ai", "status": "UP", "knowledgeEnabled": False,
            })
        finally:
            server.shutdown()
            server.server_close()
            worker.join()
