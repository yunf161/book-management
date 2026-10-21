"""交付资源边界：会话公平、模型繁忙时无残留、HTTP慢连接线程有上限。"""
import socket
import threading
import unittest
from ai_server import AiServer, Conversations
from tools.backend import ToolError
from tests.test_stage7 import FakeAssistant
from types import SimpleNamespace
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import json

class DeliveryLimitTests(unittest.TestCase):
    def test_one_member_only_recycles_own_idle_conversation(self):
        store = Conversations(capacity=4, per_owner=2)
        other, row = store.acquire(2, create=True)
        store.release(row)
        keys = []
        for _ in range(3):
            key, row = store.acquire(1, create=True)
            store.release(row)
            keys.append(key)
        self.assertIn(other,store.rows)
        self.assertNotIn(keys[0],store.rows)
        self.assertEqual(len(store.rows),3)

    def test_busy_model_does_not_leave_new_conversation(self):
        server=AiServer(("127.0.0.1",0),"internal-test-token-at-least-24", assistant_factory=lambda t,k:(FakeAssistant(),SimpleNamespace(current_token=t)))
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        server.slot.acquire()
        try:
            req=Request(f"http://127.0.0.1:{server.server_port}/internal/assistant/chat",json.dumps({"memberId":1,"question":"test"}).encode(),{"Authorization":"Bearer internal-test-token-at-least-24","X-Library-Token":"test"})
            with self.assertRaises(HTTPError) as error:urlopen(req,timeout=3)
            error.exception.read()
            error.exception.close()
            self.assertEqual(error.exception.code,503)
            self.assertEqual(len(server.conversations.rows),0)
        finally:
            server.slot.release()
            server.shutdown()
            server.server_close()
            worker.join()

    def test_connection_limit_returns_503_without_spawning_worker(self):
        server=AiServer(("127.0.0.1",0),"internal-test-token-at-least-24")
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        # 占满连接配额，真实TCP请求必须立即拒绝，不等待模型或新建线程。
        for _ in range(32):
            server.connection_slots.acquire()
        try:
            with socket.create_connection(server.server_address,timeout=3) as connection:
                self.assertIn(b"503 Service Unavailable",connection.recv(1024))
        finally:
            for _ in range(32):
                server.connection_slots.release()
            server.shutdown()
            server.server_close()
            worker.join()


class EmbeddedChromaBoundaryTests(unittest.TestCase):
    def test_environment_cannot_switch_store_to_remote_chroma_server(self):
        import tempfile
        from pathlib import Path
        from unittest.mock import patch
        from config.rag_settings import RagSettings
        from rag.store import KnowledgeStore
        from tests.test_stage3 import FakeEmbedding
        # 在已授权的数据目录内创建测试库，避免系统临时目录的访问限制。
        test_root = Path(__file__).resolve().parents[2] / "target" / "python-tests"
        test_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=test_root) as directory:
            with patch.dict("os.environ", {"CHROMA_API_IMPL": "chromadb.api.fastapi.FastAPI", "CHROMA_SERVER_HOST": "invalid.remote.test"}):
                store = KnowledgeStore(RagSettings(data_dir=Path(directory)), FakeEmbedding())
            try:
                self.assertEqual(type(store.client._server).__name__, "RustBindingsAPI")
                self.assertEqual(store.status()["chunks"], 0)
            finally:
                # Windows必须先关闭Chroma句柄，才能删除隔离测试目录。
                store.client._system.stop()
