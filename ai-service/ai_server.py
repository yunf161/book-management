"""Java的内部AI入口：保留推荐重排，增加隔离会话的只读咨询与SSE。"""

from copy import deepcopy
from dataclasses import dataclass, field, replace
import hmac
import json
import os
from pathlib import Path
import threading
import time
import uuid

from agents.read_assistant import ReadAssistant
from agents.business import BusinessAgent
from clients.model_client import create_model_client
from config.backend_settings import BackendSettings
from config.settings import Settings
from rerank_server import Handler, RerankServer
from tools.backend import LibraryReadClient, ToolError
from tools.registry import ReadTools
from rag.http_management import handle as handle_knowledge


@dataclass
class Conversation:
    owner: int
    task: str = "CONSULTATION"
    history: list = field(default_factory=list)
    sources: list = field(default_factory=list)
    next_source: int = 1
    touched: float = field(default_factory=time.monotonic)
    lock: threading.Lock = field(default_factory=threading.Lock)


class Conversations:
    """有界、可过期的临时对话；不保存业务令牌，同一对话不能同时执行两轮。"""
    def __init__(self, capacity=128, ttl=1800, per_owner=8):
        self.capacity, self.ttl, self.per_owner = capacity, ttl, per_owner
        self.rows, self.lock = {}, threading.Lock()

    def acquire(self, owner, conversation_id=None, create=False):
        if type(owner) is not int or owner <= 0:
            raise ToolError("成员格式错误", 400)
        if conversation_id is not None:
            try:
                if str(uuid.UUID(conversation_id)) != conversation_id:
                    raise ValueError()
            except (ValueError, TypeError, AttributeError):
                raise ToolError("会话ID格式错误", 400)
        with self.lock:
            now = time.monotonic()
            for key, value in list(self.rows.items()):
                if now - value.touched > self.ttl and not value.lock.locked():
                    del self.rows[key]
            if conversation_id is None and create:
                owned = [(key, value) for key, value in self.rows.items() if value.owner == owner]
                if len(owned) >= self.per_owner or len(self.rows) >= self.capacity:
                    # 一个成员只能回收自己的空闲对话，不能通过新建请求淘汰其他成员历史。
                    idle = [(key, value) for key, value in owned if not value.lock.locked()]
                    if not idle:
                        raise ToolError("会话容量已满，请稍后重试", 503)
                    del self.rows[min(idle, key=lambda row: row[1].touched)[0]]
                conversation_id = str(uuid.uuid4())
                self.rows[conversation_id] = Conversation(owner)
            row = self.rows.get(conversation_id)
            if row is None or row.owner != owner:
                raise ToolError("会话不存在或已过期", 404)
            if not row.lock.acquire(blocking=False):
                raise ToolError("该会话正在回答，请稍后重试", 409)
            row.touched = now
            return conversation_id, row

    def release(self, row):
        row.touched = time.monotonic()
        row.lock.release()


def build_assistant(token, knowledge=None):
    # 限制单次模型阻塞；回调地址由服务器环境配置决定，用户和模型都不能指定URL。
    runtime = replace(Settings.from_env(), timeout=25, max_tokens=768)
    backend = LibraryReadClient(BackendSettings.from_env(), token)
    return ReadAssistant(create_model_client(runtime), ReadTools(backend, knowledge)), backend


def build_business_assistant(token, knowledge=None):
    runtime = replace(Settings.from_env(), timeout=90, max_tokens=768)
    backend = LibraryReadClient(BackendSettings.from_env(), token)
    return BusinessAgent(create_model_client(runtime), ReadTools(backend, knowledge, business=True)), backend


class AssistantHandler(Handler):
    def authenticate(self):
        actual = self.headers.get("Authorization", "").encode()
        expected = ("Bearer " + self.server.token).encode()
        if not hmac.compare_digest(actual, expected):
            raise ToolError("内部服务凭据无效", 401)

    def event(self, name, value):
        raw = ("event: " + name + "\ndata: " + json.dumps(value, ensure_ascii=False) + "\n\n").encode()
        self.wfile.write(raw)
        self.wfile.flush()

    def do_POST(self):
        if handle_knowledge(self):
            return
        if self.path != "/internal/assistant/chat":
            return super().do_POST()
        self.connection.settimeout(30)
        row, stream, started, acquired, authenticated = None, None, False, False, False
        created, key = False, None
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 16384:
                raise ToolError("请求长度错误", 400)
            raw = self.rfile.read(length)
            self.authenticate()
            authenticated = True
            try:
                value = json.loads(raw)
            except (ValueError, UnicodeError) as exc:
                raise ToolError("请求必须为有效JSON", 400) from exc
            if not isinstance(value, dict) or set(value) - {"memberId", "conversationId", "question", "task"}:
                raise ToolError("请求字段错误", 400)
            task = value.get("task", "CONSULTATION")
            if task not in ("CONSULTATION", "BUSINESS"):
                raise ToolError("智能体类型错误", 400)
            question = value.get("question")
            if not isinstance(question, str) or not question.strip() or len(question) > 2000:
                raise ToolError("问题须为1到2000字符", 400)
            token = self.headers.get("X-Library-Token", "")
            if not token or len(token) > 8192:
                raise ToolError("缺少有效业务令牌", 401)
            key, row = self.server.conversations.acquire(value.get("memberId"), value.get("conversationId"), True)
            if value.get("conversationId") is not None and row.task != task:
                raise ToolError("切换智能体需要新建对话", 409)
            created = value.get("conversationId") is None
            row.task = task
            acquired = self.server.slot.acquire(blocking=False)
            if not acquired:
                raise ToolError("AI正在处理其他请求，请稍后重试", 503)
            factory = self.server.business_factory if task == "BUSINESS" else self.server.assistant_factory
            assistant, backend = factory(token, self.server.knowledge)
            assistant._history = deepcopy(row.history)
            assistant._sources = deepcopy(row.sources)
            assistant._next_source = row.next_source
            stream = assistant.stream(question)
            # 工具规划和查询先完成，首个回答片段产生后才发送响应头，从而透传工具401/403及JWT轮换。
            first = next(stream)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Refreshed-Token", backend.current_token)
            self.end_headers()
            started = True
            self.event("meta", {"conversationId": key, "task": task})
            total = 0
            for text in [first]:
                total += len(text)
                self.event("delta", {"text": text})
            for text in stream:
                total += len(text)
                if total > 12000:
                    raise ToolError("回答超出长度限制", 503)
                self.event("delta", {"text": text})
            if task == "BUSINESS" and getattr(assistant, "proposal", None) is not None:
                # 只是提案，Java必须再次校验并保存草稿；这里绝不调用确认或业务写接口。
                self.event("proposal", assistant.proposal)
            # ReadAssistant只有引用校验通过才提交history。断流、取消或引用错误不会保存半轮对话。
            self.event("sources", {"sources": assistant.sources})
            calls = getattr(getattr(assistant, "client", None), "usage", [])
            if calls:
                self.event("usage", {"calls": calls})
            row.history = assistant.history
            row.sources = assistant.sources
            row.next_source = assistant._next_source
            self.event("done", {"conversationId": key})
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as error:
            status = error.status if isinstance(error, ToolError) and error.status in (400, 401, 403, 404, 409, 503) else 503
            message = str(error) if isinstance(error, ToolError) else "AI回答失败，请稍后重试"
            if started:
                try:
                    self.event("error", {"code": status, "message": message})
                except OSError:
                    pass
            else:
                # 错误响应发出前释放会话，客户端收到409后可以立即继续原智能体对话。
                if row is not None:
                    if created:
                        with self.server.conversations.lock:
                            self.server.conversations.rows.pop(key, None)
                    self.server.conversations.release(row)
                    row = None
                self.reply(status, {"error": message, "sessionExpired": authenticated and status == 401})
        finally:
            # 即使生成器清理失败，也必须释放会话锁和模型并发槽。
            try:
                if stream is not None:
                    stream.close()
            finally:
                if row is not None:
                    self.server.conversations.release(row)
                if acquired:
                    self.server.slot.release()

    def conversation(self, delete=False):
        row = None
        try:
            self.authenticate()
            owner = int(self.headers.get("X-Member-Id", "0"))
            key = self.path.removeprefix("/internal/assistant/conversations/")
            key, row = self.server.conversations.acquire(owner, key)
            if delete:
                with self.server.conversations.lock:
                    del self.server.conversations.rows[key]
                self.reply(200, {"deleted": True})
            else:
                self.reply(200, {"conversationId": key, "task": row.task, "messages": row.history, "sources": row.sources})
        except Exception as error:
            status = error.status if isinstance(error, ToolError) else 400
            self.reply(status or 503, {"error": str(error) if isinstance(error, ToolError) else "会话参数错误"})
        finally:
            if row is not None:
                self.server.conversations.release(row)

    def do_GET(self):
        if self.path == "/internal/health":
            # 启动编排只检查本服务与知识库是否就绪，不触发云端或本地模型推理。
            try:
                self.authenticate()
            except ToolError as error:
                return self.reply(error.status, {"error": "unauthorized"})
            return self.reply(200, {
                "service": "ai-library-ai",
                "status": "UP",
                "knowledgeEnabled": self.server.knowledge_management is not None,
            })
        if handle_knowledge(self):
            return
        if self.path.startswith("/internal/assistant/conversations/"):
            self.conversation()
        else:
            self.reply(404, {"error": "not_found"})

    def do_DELETE(self):
        if self.path.startswith("/internal/assistant/conversations/"):
            self.conversation(True)
        else:
            self.reply(404, {"error": "not_found"})

    def do_PUT(self):
        if not handle_knowledge(self):
            self.reply(404, {"error": "not_found"})


class AiServer(RerankServer):
    def __init__(self, address, token, assistant_factory=build_assistant, knowledge=None, business_factory=build_business_assistant):
        super().__init__(address, token, handler=AssistantHandler)
        self.conversations = Conversations()
        self.assistant_factory, self.knowledge = assistant_factory, knowledge
        self.business_factory = business_factory
        self.knowledge_management = None
        if knowledge is not None:
            from rag.management import KnowledgeManagement
            manifest = os.getenv("AI_KNOWLEDGE_MANIFEST", str(Path(__file__).parent / "knowledge/library-guide/manifest.json"))
            self.knowledge_management = KnowledgeManagement(knowledge, manifest)

    def server_close(self):
        if self.knowledge_management is not None:
            self.knowledge_management.close()
        super().server_close()


if __name__ == "__main__":
    root = Path(__file__).resolve().parent
    token = os.getenv("RECOMMENDATION_AI_TOKEN", "") or (root / "data/rerank-token.txt").read_text().strip()
    knowledge = None
    # 只显式加载已审核的正式通用知识；不默认导入演示材料或本地旧政策。
    if os.getenv("AI_KNOWLEDGE_DIR"):
        from config.rag_settings import RagSettings
        from rag.embedding import OllamaEmbedding
        from rag.service import KnowledgeService
        from rag.store import KnowledgeStore
        settings = replace(RagSettings.from_env(), data_dir=Path(os.environ["AI_KNOWLEDGE_DIR"]))
        runtime = Settings.from_env()
        knowledge = KnowledgeService(KnowledgeStore(settings, OllamaEmbedding(runtime.base_url, settings.embedding_model, 20)))
    server = AiServer(("127.0.0.1", int(os.getenv("RECOMMENDATION_AI_PORT", "8001"))), token, knowledge=knowledge)
    print("本地AI服务就绪（咨询与推荐）：127.0.0.1:" + str(server.server_port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
