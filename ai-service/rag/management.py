"""受控知识文档目录与持久化索引任务；只接受正文，不接受客户端文件路径。"""

from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import sqlite3
import threading
import time
import uuid
from rag.importing import ImportValidationError, read_structured_source, validate_metadata


class ManagementError(ValueError):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


class KnowledgeManagement:
    def __init__(self, service, manifest=None):
        self.service = service
        self.root = Path(service.store.path) / "management"
        self.root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.worker = ThreadPoolExecutor(max_workers=1, thread_name_prefix="knowledge-index")
        with self.db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS catalog(id TEXT PRIMARY KEY, body TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS meta(id INTEGER PRIMARY KEY, revision INTEGER NOT NULL,
                    indexed_revision INTEGER NOT NULL, current_policies TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS job(id TEXT PRIMARY KEY, status TEXT NOT NULL,
                    revision INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0,
                    total INTEGER NOT NULL DEFAULT 0, error TEXT, created_at REAL NOT NULL,
                    finished_at REAL, generation TEXT);
            """)
            # 进程重启不能把未完成任务误报成功，旧生效指针保持不变。
            db.execute("UPDATE job SET status='FAILED',error='服务重启，需重新重建',finished_at=? WHERE status IN ('QUEUED','RUNNING')", (time.time(),))
            if db.execute("SELECT 1 FROM meta").fetchone() is None:
                original = json.loads(Path(manifest).read_text(encoding="utf-8-sig")) if manifest else {}
                for source in original.get("documents", []):
                    path = (Path(manifest).parent / source["file"]).resolve()
                    if not path.is_relative_to(Path(manifest).parent.resolve()):
                        raise ValueError("知识清单路径越界")
                    doc = dict(source)
                    doc.pop("file")
                    if path.suffix.lower() in (".csv", ".json", ".pdf"):
                        parsed = read_structured_source(path)
                        doc["content"] = parsed["content"]
                        doc["importMetadata"] = parsed["importMetadata"]
                    else:
                        doc["content"] = path.read_text(encoding="utf-8-sig")
                    doc["versionNumber"] = 0
                    doc["managed"] = doc["kind"] != "policy"
                    db.execute("INSERT INTO catalog VALUES (?,?)", (doc["id"], json.dumps(doc, ensure_ascii=False)))
                db.execute("INSERT INTO meta VALUES(1,0,0,?)", (json.dumps(original.get("current_policies", {})),))

    @contextmanager
    def db(self):
        connection = sqlite3.connect(self.root / "management.sqlite3", timeout=10)
        connection.row_factory = sqlite3.Row
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def documents(self, filters):
        with self.db() as db:
            docs = [json.loads(row[0]) for row in db.execute("SELECT body FROM catalog ORDER BY id")]
        for key in ("id", "kind", "status"):
            if filters.get(key):
                docs = [doc for doc in docs if doc[key] == filters[key]]
        if filters.get("title"):
            docs = [doc for doc in docs if filters["title"].casefold() in doc["title"].casefold()]
        page, size = max(1, int(filters.get("page", 1))), min(100, max(1, int(filters.get("pageSize", 10))))
        for doc in docs:
            doc["contentMd5"] = self.md5(doc["content"])
            doc["characters"] = len(doc.pop("content"))
            # 列表只返回来源摘要，避免成千上万行的定位偏移撑大分页响应。
            imported = doc.pop("importMetadata", None)
            if imported:
                doc.update(importFormat=imported["format"], sourceFile=imported["fileName"],
                           sourceUnits=len(imported["sections"]))
        return {"total": len(docs), "rows": docs[(page - 1) * size:page * size]}

    def document(self, doc_id):
        with self.db() as db:
            row = db.execute("SELECT body FROM catalog WHERE id=?", (doc_id,)).fetchone()
        if row is None:
            raise ManagementError("文档不存在", 404)
        return json.loads(row[0])

    @staticmethod
    def md5(text):
        return hashlib.md5(text.replace("\r\n", "\n").strip().encode(), usedforsecurity=False).hexdigest()

    def save(self, value, doc_id=None):
        if not isinstance(value, dict) or set(value) - {"title", "source", "kind", "version", "status", "content", "expectedVersion", "importMetadata"}:
            raise ManagementError("文档字段错误")
        for key, limit in (("title", 200), ("source", 500), ("version", 64), ("content", 200000)):
            if not isinstance(value.get(key), str) or not value[key].strip() or len(value[key]) > limit:
                raise ManagementError(f"{key}不能为空或超过长度限制")
        if value.get("kind") not in ("general", "book_intro") or value.get("status") not in ("ACTIVE", "DRAFT", "ARCHIVED"):
            raise ManagementError("只允许管理通用知识/书目介绍，状态必须合法；政策由政策模块维护")
        try:
            metadata = validate_metadata(value.get("importMetadata"), value["content"].strip())
        except ImportValidationError as error:
            raise ManagementError(str(error)) from error
        with self.lock, self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            old = db.execute("SELECT body FROM catalog WHERE id=?", (doc_id,)).fetchone()
            if doc_id is not None:
                if old is None:
                    raise ManagementError("文档不存在", 404)
                old = json.loads(old[0])
                if not old.get("managed"):
                    raise ManagementError("受控政策文档不可在此修改", 409)
                if type(value.get("expectedVersion")) is not int or value["expectedVersion"] != old["versionNumber"]:
                    raise ManagementError("文档版本冲突，请刷新", 409)
            digest = self.md5(value["content"])
            for row in db.execute("SELECT body FROM catalog WHERE id<>?", (doc_id or "",)):
                other = json.loads(row[0])
                if other["kind"] == value["kind"] and self.md5(other["content"]) == digest:
                    if other["content"].replace("\r\n", "\n").strip() != value["content"].replace("\r\n", "\n").strip():
                        raise ManagementError("MD5碰撞，拒绝导入", 409)
                    raise ManagementError("相同类型的正文已存在，文档ID：" + other["id"], 409)
            if old is None and db.execute("SELECT COUNT(*) FROM catalog").fetchone()[0] >= 500:
                raise ManagementError("第一版知识目录最多500份文档", 409)
            result = {key: value[key].strip() for key in ("title", "source", "kind", "version", "status", "content")}
            result.update(id=doc_id or str(uuid.uuid4()), demo=False, managed=True,
                          versionNumber=old["versionNumber"] + 1 if old else 0)
            if metadata is not None:
                result["importMetadata"] = metadata
            db.execute("INSERT INTO catalog VALUES(?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body", (result["id"], json.dumps(result, ensure_ascii=False)))
            db.execute("UPDATE meta SET revision=revision+1")
            return result

    def status(self):
        with self.db() as db:
            meta = dict(db.execute("SELECT revision,indexed_revision AS indexedRevision FROM meta").fetchone())
            job = db.execute("SELECT * FROM job ORDER BY created_at DESC LIMIT 1").fetchone()
        return dict(meta, snapshot=self.service.get_status(), latestJob=dict(job) if job else None)

    def job(self, job_id):
        with self.db() as db:
            row = db.execute("SELECT * FROM job WHERE id=?", (job_id,)).fetchone()
        if row is None:
            raise ManagementError("任务不存在", 404)
        return dict(row)

    def rebuild(self, value):
        if not isinstance(value, dict) or set(value) != {"expectedRevision", "expectedGeneration"}:
            raise ManagementError("必须提供目录版本和当前生效代")
        if type(value["expectedRevision"]) is not int or not isinstance(value["expectedGeneration"], str):
            raise ManagementError("版本格式错误")
        with self.lock, self.db() as db:
            db.execute("BEGIN IMMEDIATE")
            if db.execute("SELECT 1 FROM job WHERE status IN ('QUEUED','RUNNING')").fetchone():
                raise ManagementError("已有索引任务正在执行", 409)
            meta = db.execute("SELECT * FROM meta").fetchone()
            if meta["revision"] != value["expectedRevision"] or self.service.get_status()["generation"] != value["expectedGeneration"]:
                raise ManagementError("目录或生效索引已变化，请刷新", 409)
            job_id = str(uuid.uuid4())
            # 使用任务私有不可变输入，不受后续编辑干扰；不拼接用户提供的路径。
            directory = self.root / job_id
            directory.mkdir()
            docs = []
            for index, row in enumerate(db.execute("SELECT body FROM catalog ORDER BY id")):
                doc = json.loads(row[0])
                (directory / f"{index}.md").write_text(doc.pop("content"), encoding="utf-8")
                doc.pop("managed", None)
                doc.pop("versionNumber", None)
                doc["file"] = f"{index}.md"
                docs.append(doc)
            manifest = directory / "manifest.json"
            manifest.write_text(json.dumps({"documents": docs, "current_policies": json.loads(meta["current_policies"])}, ensure_ascii=False), encoding="utf-8")
            db.execute("INSERT INTO job(id,status,revision,created_at) VALUES(?,'QUEUED',?,?)", (job_id, meta["revision"], time.time()))
        self.worker.submit(self.run, job_id, manifest, value["expectedGeneration"])
        return self.job(job_id)

    def run(self, job_id, manifest, generation):
        try:
            with self.db() as db:
                db.execute("UPDATE job SET status='RUNNING' WHERE id=?", (job_id,))
            def progress(completed, total):
                with self.db() as db:
                    db.execute("UPDATE job SET completed=?,total=? WHERE id=?", (completed, total, job_id))
            result = self.service.store.ingest(manifest, expected_generation=generation, force=True, progress=progress)
            with self.db() as db:
                db.execute("UPDATE job SET status='SUCCEEDED',generation=?,finished_at=? WHERE id=?", (result["generation"], time.time(), job_id))
                db.execute("UPDATE meta SET indexed_revision=(SELECT revision FROM job WHERE id=?)", (job_id,))
        except Exception:
            # 不外泄本地路径、内部凭据和嵌入服务返回原文。
            with self.db() as db:
                db.execute("UPDATE job SET status='FAILED',error='索引失败，旧索引继续生效；请检查嵌入服务后重试',finished_at=? WHERE id=?", (time.time(), job_id))
        finally:
            # 任务正文只用于索引构建；结果/进度留SQLite，不无限累积明文临时副本。
            for file in manifest.parent.iterdir():
                if file.is_file() and file.suffix in (".md", ".json"):
                    file.unlink()
            manifest.parent.rmdir()

    def close(self):
        self.worker.shutdown(wait=True)
