# 仅影响当前进程；独立监听11435，不修改用户默认11434实例。
param([int]$Port = 11435)
$ErrorActionPreference = 'Stop'
$taskOllama = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
if (-not (Test-Path -LiteralPath $taskOllama)) { throw '未找到已安装的Ollama' }
$env:CUDA_VISIBLE_DEVICES = '-1'
$env:ROCR_VISIBLE_DEVICES = '-1'
$env:GGML_VK_VISIBLE_DEVICES = '0'
$env:OLLAMA_VULKAN = '1'
$env:OLLAMA_IGPU_ENABLE = '1'
$env:OLLAMA_HOST = "127.0.0.1:$Port"
$env:OLLAMA_CONTEXT_LENGTH = '32768'
# 当前设备Vulkan0是Intel核显。驱动/显卡发生变化后，应重新核对启动日志。
& $taskOllama serve
