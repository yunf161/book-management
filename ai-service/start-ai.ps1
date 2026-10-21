# 当前终端未继承新用户变量时，读取刚保存的百炼密钥；不打印密钥。
param(
    [string]$Python = "$env:USERPROFILE\miniconda3\envs\torch312\python.exe",
    [string]$OllamaUrl = 'http://127.0.0.1:11435',
    [string]$BackendUrl = 'http://127.0.0.1:8080'
)
$ErrorActionPreference = 'Stop'
# 从统一启动入口继承凭据、知识库目录和日志配置，避免两套配置漂移。
& (Join-Path $PSScriptRoot '..\scripts\start-local.ps1') -SkipBackend -SkipFrontend -PythonPath $Python -BackendPort ([uri]$BackendUrl).Port -OllamaPort ([uri]$OllamaUrl).Port
