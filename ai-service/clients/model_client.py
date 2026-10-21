"""统一模型入口：阿里云优先，调用失败后仅降级一次到本地Ollama。"""

import logging

from clients.aliyun_client import AliyunClient
from clients.ollama_client import OllamaError
from clients.tool_client import ToolOllamaClient
from config.cloud_settings import CloudSettings
from config.settings import Settings

logger = logging.getLogger(__name__)


def create_cloud_client(settings):
    try:
        cloud = CloudSettings.from_env()
        return AliyunClient(settings, cloud) if cloud.api_key else None
    except ValueError:
        logger.warning("阿里云模型配置无效，本轮使用本地Ollama")
        return None


class FallbackModelClient:
    def __init__(self, local, cloud=None):
        self.local, self.cloud = local, cloud
        self.settings = local.settings

    @property
    def usage(self):
        return list(getattr(self.cloud, "usage", [])) + list(getattr(self.local, "usage", []))

    def plan_tools(self, messages, tools):
        if self.cloud is not None:
            try:
                return self.cloud.plan_tools(messages, tools)
            except OllamaError as error:
                # AliyunClient只提供本地生成的固定错误/HTTP状态，不含云端响应正文。
                logger.warning("阿里云工具规划失败（%s），降级本地Ollama", error)
        return self.local.plan_tools(messages, tools)

    def stream_chat(self, messages):
        if self.cloud is not None:
            try:
                # 云端适配器只在整次完成后产出文本；此处也隔离未来适配器的部分输出。
                parts = list(self.cloud.stream_chat(messages))
                if not parts or not "".join(parts).strip():
                    raise OllamaError("云端空响应")
            except OllamaError as error:
                logger.warning("阿里云回答失败（%s），降级本地Ollama", error)
            else:
                yield from parts
                return
        yield from self.local.stream_chat(messages)


def create_model_client(settings=None):
    runtime = settings or Settings.from_env()
    return FallbackModelClient(ToolOllamaClient(runtime), create_cloud_client(runtime))
