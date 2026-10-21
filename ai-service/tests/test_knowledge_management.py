"""真实Chroma + SQLite，嵌入用确定性替身，不占用用户GPU。"""

from dataclasses import replace
import json
from pathlib import Path
import tempfile
import time
import unittest

from config.rag_settings import RagSettings
from rag.management import KnowledgeManagement, ManagementError
from rag.service import KnowledgeService
from rag.store import KnowledgeStore
from tests.test_stage3 import FakeEmbedding


class ManagementTests(unittest.TestCase):
    def setUp(self):
        # Chroma原生绑定在Windows上可能延迟释放文件句柄，测试结束不因临时清理失败误报业务失败。
        self.temp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.embedding = FakeEmbedding()
        self.store = KnowledgeStore(replace(RagSettings(), data_dir=Path(self.temp.name)), self.embedding)
        self.manager = KnowledgeManagement(KnowledgeService(self.store))
        self.body = dict(title="借阅使用指引", source="test://manual", kind="general", version="v1", status="ACTIVE", content="图书查询与借阅操作指引。" * 20)

    def tearDown(self):
        self.manager.close()
        self.temp.cleanup()

    def wait(self, job):
        for _ in range(100):
            result = self.manager.job(job["id"])
            if result["status"] in ("SUCCEEDED", "FAILED"):
                return result
            time.sleep(.05)
        self.fail("索引任务未结束")

    def rebuild(self):
        status = self.manager.status()
        return self.manager.rebuild(dict(expectedRevision=status["revision"], expectedGeneration=status["snapshot"]["generation"]))

    def test_create_list_filter_detail_and_md5(self):
        doc = self.manager.save(self.body)
        result = self.manager.documents(dict(title="借阅", kind="general", page=1, pageSize=10))
        self.assertEqual(1, result["total"])
        self.assertNotIn("content", result["rows"][0])
        self.assertEqual(32, len(result["rows"][0]["contentMd5"]))
        self.assertEqual(self.body["content"], self.manager.document(doc["id"])["content"])
        with self.assertRaises(ManagementError):
            self.manager.save(self.body)

    def test_version_conflict_and_bad_fields(self):
        doc = self.manager.save(self.body)
        with self.assertRaises(ManagementError):
            self.manager.save(dict(self.body, expectedVersion=3), doc["id"])
        self.assertEqual(1, self.manager.save(dict(self.body, expectedVersion=0, title="新版"), doc["id"])["versionNumber"])
        for body in (dict(self.body, file="C:/secret"), dict(self.body, kind="policy"), dict(self.body, content="")):
            with self.subTest(body=body), self.assertRaises(ManagementError):
                self.manager.save(body)

    def test_rebuild_progress_new_generation_and_archive(self):
        doc = self.manager.save(self.body)
        first = self.wait(self.rebuild())
        self.assertEqual("SUCCEEDED", first["status"])
        self.assertGreater(first["total"], 0)
        self.assertEqual(first["total"], first["completed"])
        second = self.wait(self.rebuild())
        self.assertNotEqual(first["generation"], second["generation"])
        self.assertEqual(1, len(self.store.query("借阅", min_similarity=0)))
        self.manager.save(dict(self.body, status="ARCHIVED", expectedVersion=0), doc["id"])
        self.wait(self.rebuild())
        self.assertEqual([], self.store.query("借阅", min_similarity=0))

    def test_failed_index_keeps_old_pointer_and_retry(self):
        self.manager.save(self.body)
        old = self.wait(self.rebuild())["generation"]
        self.embedding.fail = True
        failed = self.wait(self.rebuild())
        self.assertEqual("FAILED", failed["status"])
        self.assertEqual(old, self.store.status()["generation"])
        self.assertNotIn(str(self.temp.name), failed["error"])
        self.embedding.fail = False
        self.assertEqual("SUCCEEDED", self.wait(self.rebuild())["status"])

    def test_stale_catalog_or_generation_rejected(self):
        self.manager.save(self.body)
        for body in (dict(expectedRevision=0, expectedGeneration=""), dict(expectedRevision=1, expectedGeneration="stale")):
            with self.assertRaises(ManagementError):
                self.manager.rebuild(body)

if __name__ == "__main__":
    unittest.main()
