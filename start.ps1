<# 项目统一入口：参数原样交给启动脚本，脚本不依赖调用时所在目录。 #>
& (Join-Path $PSScriptRoot 'scripts\start-local.ps1') @args
