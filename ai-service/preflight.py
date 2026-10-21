"""启动前检查依赖；使用文件入口，避免Windows PowerShell 5.1破坏-c参数引号。"""

import chromadb
import fsspec
import pypdf

# Python会把本文件目录加入sys.path，从项目外启动也能验证本服务模块。
from config.backend_settings import BackendSettings


if __name__ == "__main__":
    BackendSettings.from_env()
    print("Python dependencies ready", flush=True)
