"""Chroma持久化与知识快照指针：完成向量写入后才发布新快照。"""

from contextlib import contextmanager
import hashlib
import json
import sqlite3
import time
import uuid
from pathlib import Path

from rag.documents import load_manifest


class KnowledgeStore:
    def __init__(self, settings, embedding):
        try:
            import chromadb
            from chromadb.config import Settings as ChromaSettings
        except ImportError as exc:
            raise ValueError("缺少Chroma，请在torch312环境安装requirements.txt") from exc
        self.settings = settings
        self.embedding = embedding
        self.path = Path(settings.data_dir).resolve()
        self.path.mkdir(parents=True, exist_ok=True)
        self.db_path = self.path / "registry.sqlite3"
        self.client = chromadb.PersistentClient(
            path=str(self.path / "chroma"),
            # 显式锁定本地Rust嵌入式存储，不读取环境变量指定的远端Chroma API。
            # 不启动Chroma HTTP端点，也不接受外部collection配置或trust_remote_code。
            settings=ChromaSettings(
                anonymized_telemetry=False,
                chroma_api_impl="chromadb.api.rust.RustBindingsAPI",
                chroma_server_host=None,
            ),
        )
        with self._db() as db:
            db.execute("CREATE TABLE IF NOT EXISTS snapshot (id INTEGER PRIMARY KEY CHECK(id=1), generation TEXT NOT NULL, collection_name TEXT NOT NULL, embedding_id TEXT NOT NULL, chunk_count INTEGER NOT NULL)")

    @contextmanager
    def _db(self):
        db = sqlite3.connect(self.db_path, timeout=30)
        try:
            with db:
                yield db
        finally:
            db.close()

    def ingest(self, manifest, expected_generation=None, *, force=False, progress=None):
        rows, content_hash = load_manifest(manifest, self.settings)
        identity = self.embedding.identity
        generation = hashlib.sha256((identity + content_hash).encode()).hexdigest()
        # 重建必须写入新的不可变代，不能原地覆盖正在被查询的向量。
        if force:
            generation = hashlib.sha256((generation + uuid.uuid4().hex).encode()).hexdigest()
        if progress:
            progress(0, len(rows))
        name = "library_" + hashlib.sha256(identity.encode()).hexdigest()[:24]
        # BEGIN IMMEDIATE串行化导入。查询不拿写锁，只读取已提交的快照。
        with self._db() as db:
            db.execute("BEGIN IMMEDIATE")
            previous = db.execute("SELECT generation, chunk_count FROM snapshot WHERE id=1").fetchone()
            if expected_generation is not None:
                actual = previous[0] if previous else ""
                if expected_generation != actual:
                    raise ValueError("知识库版本冲突，请先读取最新快照再更新")
            if previous and previous[0] == generation:
                return {"generation": generation, "chunks": previous[1], "changed": False}
            collection = self.client.get_or_create_collection(
                name=name, embedding_function=None,
                configuration={"hnsw": {"space": "cosine"}},
                metadata={"embedding_id": identity},
            )
            for offset in range(0, len(rows), self.settings.batch_size):
                batch = rows[offset:offset + self.settings.batch_size]
                texts = [row["text"] for row in batch]
                vectors = self.embedding.embed(texts)
                collection.upsert(
                    ids=[generation + ":" + str(offset + i) for i in range(len(batch))],
                    embeddings=vectors, documents=texts,
                    metadatas=[dict(row["metadata"], generation=generation) for row in batch],
                )
                if progress:
                    progress(min(offset + len(batch), len(rows)), len(rows))
            # Chroma与SQLite不属于同一事务。新向量预写入，失败时不切换指针；
            # 未发布/旧快照向量不会被检索，保留旧快照避免并发查询被清理打断。
            db.execute("INSERT INTO snapshot VALUES (1,?,?,?,?) ON CONFLICT(id) DO UPDATE SET generation=excluded.generation, collection_name=excluded.collection_name, embedding_id=excluded.embedding_id, chunk_count=excluded.chunk_count",
                       (generation, name, identity, len(rows)))
        return {"generation": generation, "chunks": len(rows), "changed": True}

    def status(self):
        """供知识更新方读取版本，查询不触发模型下载或嵌入。"""
        with self._db() as db:
            row = db.execute("SELECT generation,embedding_id,chunk_count FROM snapshot WHERE id=1").fetchone()
        return {"generation": row[0] if row else "", "embedding_id": row[1] if row else None,
                "chunks": row[2] if row else 0}

    def query(self, question, *, kind=None, policy_id=None, version=None, demo=False,
              top_k=None, min_similarity=None, now=None):
        """返回片段和来源，不调用聊天模型；空库/无匹配返回空列表。"""
        if not isinstance(question, str) or not question.strip() or len(question) > 4000:
            raise ValueError("检索问题需1～4000字符")
        top_k = self.settings.top_k if top_k is None else top_k
        threshold = self.settings.min_similarity if min_similarity is None else min_similarity
        if not 1 <= top_k <= 20 or not 0 <= threshold <= 1:
            raise ValueError("top_k需1～20，相似度需0～1")
        if kind is not None and kind not in ("policy", "general", "book_intro"):
            raise ValueError("非法知识类型")
        if version and not policy_id:
            raise ValueError("指定政策版本时必须提供政策ID")
        with self._db() as db:
            snapshot = db.execute("SELECT generation,collection_name,embedding_id,chunk_count FROM snapshot WHERE id=1").fetchone()
        if snapshot is None or snapshot[3] == 0:
            return []
        generation, name, identity, _ = snapshot
        if identity != self.embedding.identity:
            raise ValueError("嵌入模型或digest已变化，必须重新导入知识库")
        collection = self.client.get_collection(name, embedding_function=None)
        current_time = time.time() if now is None else now
        filters = [
            {"generation": generation}, {"eligible": True}, {"status": "ACTIVE"},
            {"demo": bool(demo)}, {"effective_from": {"$lte": current_time}},
            {"effective_to": {"$gt": current_time}},
        ]
        for field, value in (("kind", kind), ("policy_id", policy_id), ("version", version)):
            if value is not None:
                filters.append({field: value})
        result = collection.query(
            query_embeddings=self.embedding.embed([question.strip()], query=True),
            n_results=top_k, where={"$and": filters},
            include=["documents", "metadatas", "distances"],
        )
        hits = []
        for key, text, metadata, distance in zip(result["ids"][0], result["documents"][0], result["metadatas"][0], result["distances"][0]):
            similarity = max(-1.0, min(1.0, 1 - float(distance)))
            if similarity < threshold:
                continue
            hits.append({"chunk_id": key, "text": text, "similarity": round(similarity, 6), "metadata": metadata})
        return hits


def format_sources(hits):
    """稳定编号用于后续回答引用，阶段3只展示检索证据。"""
    return "\n\n".join(
        f"[S{i}] {h['metadata']['title']} | 版本 {h['metadata']['version']} | 来源 {h['metadata']['source']} | 定位 {h['metadata'].get('source_locations', '正文')} | 字符 {h['metadata']['start']}～{h['metadata']['end']} | 相似度 {h['similarity']}"
        + (" | 测试资料，非正式政策" if h['metadata']['demo'] else "")
        + " | MD5 " + h["metadata"]["content_md5"]
        + (" | 全部来源 " + h["metadata"]["provenance_json"] if len(json.loads(h["metadata"]["provenance_json"])) > 1 else "")
        + "\n" + h["text"] for i, h in enumerate(hits, 1)
    )
