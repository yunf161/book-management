"""内部管理协议：Java已完成成员权限校验，这里仍校验服务间凭据。"""

import json
from urllib.parse import parse_qs, urlsplit

from rag.management import ManagementError
from rag.importing import ImportValidationError, preview


def handle(handler):
    path = urlsplit(handler.path)
    if not path.path.startswith("/internal/knowledge/"):
        return False
    try:
        method = handler.command
        body = None
        if method in ("POST", "PUT"):
            size = int(handler.headers.get("Content-Length", "0"))
            maximum = 6000000 if path.path == "/internal/knowledge/import-preview" else 1200000
            if not 0 < size <= maximum:
                raise ManagementError("请求体过大或为空")
            body = json.loads(handler.rfile.read(size))
        handler.authenticate()
        manager = handler.server.knowledge_management
        if manager is None:
            raise ManagementError("知识库未配置", 503)
        query = {key: values[-1] for key, values in parse_qs(path.query).items()}
        suffix = path.path.removeprefix("/internal/knowledge/")
        if method == "POST" and suffix == "import-preview":
            result = preview(body)
        elif method == "GET" and suffix == "documents":
            result = manager.documents(query)
        elif method == "GET" and suffix == "status":
            result = manager.status()
        elif method == "GET" and suffix.startswith("documents/"):
            result = manager.document(suffix.removeprefix("documents/"))
        elif method == "GET" and suffix.startswith("jobs/"):
            result = manager.job(suffix.removeprefix("jobs/"))
        elif method == "POST" and suffix == "documents":
            result = manager.save(body)
        elif method == "PUT" and suffix.startswith("documents/"):
            result = manager.save(body, suffix.removeprefix("documents/"))
        elif method == "POST" and suffix == "rebuild":
            result = manager.rebuild(body)
        elif method == "POST" and suffix == "search":
            if not isinstance(body, dict) or set(body) - {"question", "kind", "topK"}:
                raise ManagementError("检索参数错误")
            top_k = body.get("topK", 4)
            if type(top_k) is not int or not 1 <= top_k <= 20:
                raise ManagementError("topK必须为1到20")
            result = manager.service.search(body.get("question"), kind=body.get("kind"), top_k=top_k)
        else:
            raise ManagementError("接口不存在", 404)
        handler.reply(200, result)
    except Exception as error:
        status = getattr(error, "status", 400 if isinstance(error, (ValueError, TypeError)) else 503)
        handler.reply(status, {"error": str(error) if isinstance(error, (ManagementError, ImportValidationError)) else "知识管理请求失败，请核对参数或服务状态"})
    return True
