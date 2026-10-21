"""RAG配置、解析、协议及真实Chroma存储测试，不依赖外部模型。"""

import io
import json
import shutil
import unittest
import uuid
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from config.rag_settings import ROOT, RagSettings
from rag.documents import load_manifest, split_text, timestamp
from rag.embedding import OllamaEmbedding
from rag.store import KnowledgeStore, format_sources
from clients.ollama_client import OllamaError


class FakeEmbedding:
    identity = 'test-embedding-v1'
    fail = False

    def embed(self, texts, query=False):
        if self.fail:
            raise OllamaError('测试嵌入失败')
        return [[1.0, 0.2, 0.1] for _ in texts]


class DocumentTests(unittest.TestCase):
    def test_split_coverage_overlap_and_bounds(self):
        text = ('借阅规则。\n\n中文文本abc123\n' * 50)
        chunks = list(split_text(text, 100, 20))
        covered = set()
        for start, end, content in chunks:
            self.assertEqual(content, text[start:end])
            self.assertLessEqual(len(content), 100)
            covered.update(range(start, end))
        self.assertEqual(covered, set(range(len(text))))
        self.assertLess(chunks[1][0], chunks[0][1])

    def test_empty_and_invalid_split(self):
        self.assertEqual(list(split_text('', 100, 10)), [])
        with self.assertRaises(ValueError):
            list(split_text('test', 10, 10))

    def test_settings(self):
        for kwargs in ({'chunk_size': 0}, {'overlap': 700}, {'top_k': 21}, {'min_similarity': float('nan')}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                RagSettings(**kwargs)

    def test_dates_require_timezone(self):
        self.assertEqual(timestamp('2020-01-01T00:00:00Z', 0), 1577836800)
        with self.assertRaises(ValueError):
            timestamp('2020-01-01', 0)

    def test_demo_current_version(self):
        rows, digest = load_manifest(ROOT/'knowledge/demo/manifest.json', RagSettings())
        policies = [r for r in rows if r['metadata']['kind'] == 'policy' and r['metadata']['eligible']]
        self.assertTrue(policies)
        self.assertTrue(all(r['metadata']['version'] == 'v2' for r in policies))
        self.assertEqual(len(digest), 64)


class EmbeddingTests(unittest.TestCase):
    def test_cpu_batch_and_query_instruction(self):
        vector = {'embeddings': [[0.1, 0.2]]}
        with patch('rag.embedding.urlopen', return_value=io.BytesIO(json.dumps(vector).encode())) as opened:
            self.assertEqual(OllamaEmbedding('http://localhost', 'test').embed(['问题'], query=True), [[0.1, 0.2]])
            payload = json.loads(opened.call_args.args[0].data)
            self.assertEqual(payload['options']['num_gpu'], 0)
            self.assertFalse(payload['truncate'])
            self.assertTrue(payload['input'][0].startswith('Instruct:'))

    def test_invalid_vectors(self):
        for vectors in ([], [[0, 0]], [[float('nan')]], [[True]], [[1], [1, 2]]):
            with self.subTest(vectors=vectors), patch.object(OllamaEmbedding, '_request', return_value={'embeddings': vectors}):
                with self.assertRaises(OllamaError):
                    OllamaEmbedding('http://localhost', 'test').embed(['a'])

    def test_identity_includes_digest(self):
        with patch.object(OllamaEmbedding, '_request', return_value={'models':[{'name':'test','digest':'digest123'}]}):
            self.assertIn('digest123', OllamaEmbedding('http://localhost', 'test').identity)

    def test_missing_embedding(self):
        with patch.object(OllamaEmbedding, '_request', return_value={'models':[]}):
            with self.assertRaisesRegex(OllamaError, '未找到'):
                _ = OllamaEmbedding('http://localhost', 'test').identity


class StoreTests(unittest.TestCase):
    def setUp(self):
        # Windows持久化向量库可能持有文件句柄，测试目录隔离且保留，不递归强删。
        self.folder = ROOT.parent/'target'/'python-tests'/uuid.uuid4().hex
        self.folder.mkdir(parents=True)
        shutil.copytree(ROOT/'knowledge/demo', self.folder/'source')
        self.manifest = self.folder/'source/manifest.json'
        self.settings = RagSettings(data_dir=self.folder/'db', min_similarity=0)
        self.embedding = FakeEmbedding()
        self.store = KnowledgeStore(self.settings, self.embedding)

    def change(self, mutate):
        manifest = json.loads(self.manifest.read_text(encoding='utf-8'))
        mutate(manifest)
        self.manifest.write_text(json.dumps(manifest), encoding='utf-8')

    def test_empty_library(self):
        self.assertEqual(self.store.query('期限'), [])

    def test_idempotent_and_source_mapping(self):
        first = self.store.ingest(self.manifest)
        self.assertTrue(first['changed'])
        self.assertFalse(self.store.ingest(self.manifest)['changed'])
        hits = self.store.query('期限', demo=True, kind='policy')
        self.assertTrue(hits)
        self.assertTrue(all(h['metadata']['version'] == 'v2' for h in hits))
        self.assertIn('[S1]', format_sources(hits))
        self.assertIn('demo://', format_sources(hits))
        self.assertIn('测试资料', format_sources(hits))

    def test_demo_and_old_version_excluded(self):
        self.store.ingest(self.manifest)
        self.assertEqual(self.store.query('期限'), [])
        self.assertEqual(self.store.query('期限', demo=True, policy_id='borrow-rules', version='v1'), [])
        self.assertEqual(self.store.query('期限', demo=True, policy_id='borrow-rules', version='v3'), [])

    def test_future_and_expired_excluded(self):
        def mutate(m):
            for d in m['documents']:
                if d['id'] == 'borrowing-v2':
                    d['effective_from'] = '2030-01-01T00:00:00Z'
                    d['effective_to'] = '2031-01-01T00:00:00Z'
        self.change(mutate)
        self.store.ingest(self.manifest)
        self.assertEqual(self.store.query('期限', demo=True, kind='policy', now=1), [])
        self.assertTrue(self.store.query('期限', demo=True, kind='policy', now=1900000000))
        self.assertEqual(self.store.query('期限', demo=True, kind='policy', now=2000000000), [])

    def test_removed_document_not_returned(self):
        self.store.ingest(self.manifest)
        self.change(lambda m: m['documents'].__setitem__(slice(None), [d for d in m['documents'] if d['kind'] == 'policy']))
        self.store.ingest(self.manifest)
        self.assertEqual(self.store.query('Java', demo=True, kind='book_intro'), [])

    def test_failed_import_keeps_previous_snapshot(self):
        self.store.ingest(self.manifest)
        before = self.store.query('期限', demo=True, kind='policy')[0]['chunk_id']
        self.change(lambda m: m['documents'][1].update(title='新标题'))
        self.embedding.fail = True
        with self.assertRaises(OllamaError):
            self.store.ingest(self.manifest)
        self.embedding.fail = False
        self.assertEqual(self.store.query('期限', demo=True, kind='policy')[0]['chunk_id'], before)

    def test_changed_embedding_requires_reimport(self):
        self.store.ingest(self.manifest)
        self.embedding.identity = 'different-digest'
        with self.assertRaisesRegex(ValueError, '重新导入'):
            self.store.query('期限', demo=True)

    def test_invalid_manifest_never_published(self):
        self.change(lambda m: m['documents'][0].update(file='../../outside.md'))
        with self.assertRaisesRegex(ValueError, '路径越界'):
            self.store.ingest(self.manifest)
        self.assertEqual(self.store.query('期限'), [])

    def test_broken_policy_pointer(self):
        self.change(lambda m: m['current_policies'].update({'borrow-rules':'v9'}))
        with self.assertRaisesRegex(ValueError, '不存在'):
            self.store.ingest(self.manifest)

    def test_duplicate_document(self):
        self.change(lambda m: m['documents'].append(m['documents'][0].copy()))
        with self.assertRaisesRegex(ValueError, 'ID重复'):
            self.store.ingest(self.manifest)

    def test_similarity_threshold_rejects_unrelated_vectors(self):
        self.store.ingest(self.manifest)
        self.embedding.embed = lambda texts, query=False: [[0, 1, 0] for _ in texts]
        self.assertEqual(self.store.query('不相关问题', demo=True, min_similarity=0.8), [])

    def test_read_during_import_sees_complete_old_snapshot(self):
        from concurrent.futures import ThreadPoolExecutor
        from threading import Event
        self.store.ingest(self.manifest)
        before = self.store.query('期限', demo=True, kind='policy')[0]['metadata']['title']
        self.change(lambda m: m['documents'][1].update(title='发布新标题'))
        entered, release = Event(), Event()
        original = self.embedding.embed

        def blocked(texts, query=False):
            if not query:
                entered.set()
                if not release.wait(5):
                    raise RuntimeError('测试等待超时')
            return original(texts, query=query)

        self.embedding.embed = blocked
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(self.store.ingest, self.manifest)
            try:
                self.assertTrue(entered.wait(5))
                self.assertEqual(self.store.query('期限', demo=True, kind='policy')[0]['metadata']['title'], before)
            finally:
                release.set()
            self.assertTrue(future.result(timeout=5)['changed'])
        self.assertEqual(self.store.query('期限', demo=True, kind='policy')[0]['metadata']['title'], '发布新标题')

    def test_update_service_and_conflict(self):
        from rag.service import KnowledgeService
        service = KnowledgeService(self.store)
        self.assertEqual(service.get_status()['generation'], '')
        first = service.update_knowledge(self.manifest, expected_generation='')
        self.assertEqual(service.get_status()['generation'], first['generation'])
        self.change(lambda m: m['documents'][1].update(title='新版本'))
        with self.assertRaisesRegex(ValueError, '版本冲突'):
            service.update_knowledge(self.manifest, expected_generation='stale')
        second = service.update_knowledge(self.manifest, expected_generation=first['generation'])
        self.assertNotEqual(second['generation'], first['generation'])
        self.assertEqual(service.search('期限', demo=True, kind='policy')[0]['metadata']['title'], '新版本')

    def test_policy_pointer_switch(self):
        self.store.ingest(self.manifest)
        self.change(lambda m: m['current_policies'].update({'borrow-rules':'v1'}))
        self.store.ingest(self.manifest)
        hits = self.store.query('期限', demo=True, kind='policy')
        self.assertTrue(hits)
        self.assertTrue(all(h['metadata']['version'] == 'v1' for h in hits))
        self.assertEqual(self.store.query('期限', demo=True, policy_id='borrow-rules', version='v2'), [])

    def test_md5_deduplicates_content_and_preserves_sources(self):
        import hashlib
        original, _ = load_manifest(self.manifest, self.settings)
        def duplicate(m):
            copy = next(d for d in m['documents'] if d['id'] == 'opening').copy()
            copy.update(id='opening-copy', source='demo://another-source', title='来源二')
            m['documents'].append(copy)
        self.change(duplicate)
        rows, _ = load_manifest(self.manifest, self.settings)
        self.assertEqual(len(rows), len(original))
        row = next(r for r in rows if r['metadata']['document_id'] == 'opening')
        expected = hashlib.md5(row['text'].encode('utf-8'), usedforsecurity=False).hexdigest()
        self.assertEqual(row['metadata']['content_md5'], expected)
        self.assertEqual(len(json.loads(row['metadata']['provenance_json'])), 2)
        self.store.ingest(self.manifest)
        hits = self.store.query('开放', demo=True, kind='general')
        self.assertEqual(len(hits), 1)
        self.assertIn('demo://another-source', format_sources(hits))

    def test_md5_normalizes_line_endings(self):
        first, _ = load_manifest(self.manifest, self.settings)
        file = self.manifest.parent/'opening.md'
        file.write_bytes(file.read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
        second, _ = load_manifest(self.manifest, self.settings)
        self.assertEqual(first, second)

    def test_identical_content_different_policy_versions_retained(self):
        v2 = self.manifest.parent/'borrowing-v2.md'
        (self.manifest.parent/'borrowing-v1.md').write_bytes(v2.read_bytes())
        rows, _ = load_manifest(self.manifest, self.settings)
        policy = [r for r in rows if r['metadata']['policy_id'] == 'borrow-rules']
        self.assertIn('v1', {r['metadata']['version'] for r in policy})
        self.assertIn('v2', {r['metadata']['version'] for r in policy})

    def test_md5_collision_rejected(self):
        from unittest.mock import MagicMock
        (self.manifest.parent/'different.md').write_text('不同正文', encoding='utf-8')
        def duplicate(m):
            copy = next(d for d in m['documents'] if d['id'] == 'opening').copy()
            copy.update(id='collision', file='different.md')
            m['documents'].append(copy)
        self.change(duplicate)
        digest = MagicMock()
        digest.hexdigest.return_value = 'same-md5'
        with patch('rag.documents.hashlib.md5', return_value=digest):
            with self.assertRaisesRegex(ValueError, 'MD5碰撞'):
                load_manifest(self.manifest, self.settings)

    def test_concurrent_updates_only_one_publishes(self):
        from concurrent.futures import ThreadPoolExecutor
        first = self.store.ingest(self.manifest)
        self.change(lambda m: m['documents'][1].update(title='并发新标题'))

        def publish():
            try:
                self.store.ingest(self.manifest, expected_generation=first['generation'])
                return 'published'
            except ValueError as exc:
                self.assertIn('版本冲突', str(exc))
                return 'conflict'

        with ThreadPoolExecutor(max_workers=2) as pool:
            outcomes = list(pool.map(lambda _: publish(), range(2)))
        self.assertCountEqual(outcomes, ['published', 'conflict'])

    def test_same_policy_version_conflicting_text_rejected(self):
        (self.manifest.parent/'conflict.md').write_text('不同的政策正文', encoding='utf-8')
        def duplicate(m):
            copy = next(d for d in m['documents'] if d['id'] == 'borrowing-v2').copy()
            copy.update(id='conflicting-source', file='conflict.md')
            m['documents'].append(copy)
        self.change(duplicate)
        with self.assertRaisesRegex(ValueError, '冲突正文'):
            self.store.ingest(self.manifest)

    def test_query_validation(self):
        for kwargs in ({'top_k':0}, {'min_similarity':2}, {'version':'v1'}, {'kind':'bad'}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                self.store.query('期限', **kwargs)


if __name__ == '__main__':
    unittest.main()
