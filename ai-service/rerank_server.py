"""仅监听本机的内部重排服务；Java负责鉴权、业务合法性、落库和完整降级。"""

import hmac
import json
import os
import threading
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from recommendation_rerank import rerank, validate_input


class RerankServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, token, ranker=rerank, handler=None):
        if len(token) < 24:
            raise ValueError("RECOMMENDATION_AI_TOKEN至少24位，且必须与Java配置相同")
        super().__init__(address, handler or Handler)
        self.token, self.ranker = token, ranker
        self.slot = threading.BoundedSemaphore(1)
        self.connection_slots = threading.BoundedSemaphore(32)

    def get_request(self):
        connection, address = super().get_request()
        # 请求头尚未读完时同样有超时，防止慢连接永久占用线程。
        connection.settimeout(10)
        return connection, address

    def process_request(self, request, address):
        if not self.connection_slots.acquire(blocking=False):
            try:
                body = b'{"error":"busy"}'
                request.sendall(b'HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: ' + str(len(body)).encode() + b'\r\n\r\n' + body)
            finally:
                self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.connection_slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.connection_slots.release()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        # 不把请求正文和凭据打印到控制台；调用方记录降级原因。
        pass

    def reply(self, status, value):
        raw = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        self.connection.settimeout(5)
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 65536:
                return self.reply(413, {"error": "body_size"})
            # 先消费有界请求体再拒绝，避免Windows因关闭带未读数据的连接而发RST，吞掉401响应。
            raw = self.rfile.read(length)
            if self.path != "/internal/recommendations/rerank":
                return self.reply(404, {"error": "not_found"})
            if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + self.server.token):
                return self.reply(401, {"error": "unauthorized"})
            value = validate_input(json.loads(raw))
        except (ValueError, OSError):
            return self.reply(400, {"error": "invalid_input"})
        # 多个请求不在GPU前无限排队，繁忙时立即让Java回退规则算法。
        if not self.server.slot.acquire(blocking=False):
            return self.reply(503, {"error": "busy"})
        try:
            result = self.server.ranker(value)
            status = 200
        except Exception:
            result, status = {"error": "rerank_failed"}, 502
        finally:
            self.server.slot.release()
        self.reply(status, result)


if __name__ == "__main__":
    # 本机配置文件位于已忽略的data目录；部署时优先使用环境变量覆盖。
    token_file = Path(__file__).parent / "data" / "rerank-token.txt"
    token = os.getenv("RECOMMENDATION_AI_TOKEN", "")
    if not token and token_file.exists():
        token = token_file.read_text(encoding="utf-8").strip()
    server = RerankServer(("127.0.0.1", int(os.getenv("RECOMMENDATION_AI_PORT", "8001"))),
                          token)
    print("本地推荐重排服务就绪：127.0.0.1:" + str(server.server_port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
