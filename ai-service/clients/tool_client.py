"""Ollama原生工具规划调用；最终回答复用既有流式客户端。"""

import json
from urllib.error import HTTPError,URLError
from urllib.request import Request,urlopen
from clients.ollama_client import OllamaClient,OllamaError
from clients.usage import record


class ToolOllamaClient(OllamaClient):
    def plan_tools(self,messages,tools):
        payload={"model":self.settings.model,"messages":messages,"tools":tools,
                 "think":False,"stream":False,"options":{"num_ctx":self.settings.context,
                 "num_predict":self.settings.max_tokens,"temperature":self.settings.temperature}}
        # 仅显式配置时限制设备层数/线程；默认保持服务端设备选择。
        if self.settings.num_gpu is not None:
            payload["options"]["num_gpu"] = self.settings.num_gpu
        if self.settings.num_thread is not None:
            payload["options"]["num_thread"] = self.settings.num_thread
        request=Request(self.settings.base_url.rstrip('/')+'/api/chat',data=json.dumps(payload,ensure_ascii=False).encode('utf-8'),
                        headers={'Content-Type':'application/json'},method='POST')
        try:
            with urlopen(request,timeout=self.settings.timeout) as response:
                data=response.read(1_000_001)
            if len(data)>1_000_000:
                raise OllamaError('工具规划响应过大')
            value=json.loads(data)
            record(self, "ollama", self.settings.model, value)
            if not isinstance(value,dict) or not value.get('done') or value.get('done_reason')=='length' or value.get('error'):
                raise OllamaError('工具规划未完整成功，请检查模型或输出上限')
            message=value.get('message')
            if not isinstance(message,dict) or not isinstance(message.get('tool_calls',[]),list):
                raise OllamaError('工具规划格式错误')
            return {'role':'assistant','content':'','tool_calls':message.get('tool_calls',[])}
        except HTTPError as exc:
            raise OllamaError(f'模型工具规划失败（HTTP {exc.code}）') from exc
        except (URLError,TimeoutError,OSError) as exc:
            raise OllamaError('模型工具规划连接失败或超时') from exc
        except (ValueError,UnicodeError) as exc:
            raise OllamaError('模型工具规划不是有效JSON') from exc
