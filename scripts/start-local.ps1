<#
本机统一启动：Ollama(独立核显) -> Python AI -> Java -> 可选前端。
只复用经过身份核对的服务；停止时仅处理本脚本记录且启动时间一致的进程。
不创建数据库/容器，不修改系统环境变量，不把密钥放入参数或进程清单。
#>
[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status', 'Check')]
    [string]$Action = 'Start',
    [string]$PythonPath,
    [string]$JavaPath,
    [ValidateRange(1024, 65535)][int]$BackendPort = 8080,
    [ValidateRange(1024, 65535)][int]$AiPort = 8001,
    [ValidateRange(1024, 65535)][int]$FrontendPort = 5173,
    [ValidateRange(1024, 65535)][int]$OllamaPort = 11435,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipOllama,
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$aiRoot = Join-Path $projectRoot 'ai-service'
$runtimeRoot = Join-Path $projectRoot 'target\local-runtime'
$stateFile = Join-Path $runtimeRoot 'processes.json'
$script:originalEnvironment = @{}
$script:owned = @()
$script:launched = @()
$mutex = $null
$hasMutex = $false

function Set-ChildEnvironment([string]$Name, [string]$Value) {
    if (-not $script:originalEnvironment.ContainsKey($Name)) {
        $script:originalEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Import-LocalConfiguration {
    # 当前进程显式配置优先，其次用户环境变量，再读被Git忽略的本地配置。
    foreach ($key in @('DASHSCOPE_API_KEY','DASHSCOPE_BASE_URL','DASHSCOPE_MODEL',
        'OSS_ACCESS_KEY_ID','OSS_ACCESS_KEY_SECRET','ALIBABA_CLOUD_ACCESS_KEY_ID',
        'ALIBABA_CLOUD_ACCESS_KEY_SECRET','AI_PYTHON','JAVA_HOME')) {
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
            $value = [Environment]::GetEnvironmentVariable($key, 'User')
            if ($value) { Set-ChildEnvironment $key $value }
        }
    }
    $localFile = Join-Path $projectRoot 'local-secrets.properties'
    if (Test-Path -LiteralPath $localFile) {
        foreach ($line in Get-Content -LiteralPath $localFile -Encoding UTF8) {
            if ($line -match '^([A-Z][A-Z0-9_]+)\s*=\s*(.*)$') {
                $key, $value = $Matches[1], $Matches[2].Trim()
                if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
                    Set-ChildEnvironment $key $value
                }
            }
        }
    }
}

function Test-Tcp([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync($HostName, $Port)
        if (-not $pending.Wait(1500)) { return $false }
        return $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Read-JsonHttp([string]$Url, [hashtable]$Headers = @{}) {
    # HttpClient用于兼容Windows PowerShell和PowerShell7，失败响应也读取状态码。
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(3)
    try {
        foreach ($key in $Headers.Keys) { $client.DefaultRequestHeaders.Add($key, $Headers[$key]) }
        $response = $client.GetAsync($Url).GetAwaiter().GetResult()
        try {
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            return @{ status = [int]$response.StatusCode; body = ($text | ConvertFrom-Json) }
        } finally { $response.Dispose() }
    } finally { $client.Dispose() }
}

function Test-Ready([string]$Name) {
    try {
        switch ($Name) {
            'ai' {
                $result = Read-JsonHttp "http://127.0.0.1:$AiPort/internal/health" @{
                    Authorization = "Bearer $env:RECOMMENDATION_AI_TOKEN"
                }
                return $result.status -eq 200 -and $result.body.service -eq 'ai-library-ai' -and
                    $result.body.status -eq 'UP' -and $result.body.knowledgeEnabled
            }
            'backend' {
                $result = Read-JsonHttp "http://127.0.0.1:$BackendPort/me/permissions"
                return $result.status -eq 401 -and $result.body.code -eq 401
            }
            'ollama' {
                $result = Read-JsonHttp "http://127.0.0.1:$OllamaPort/api/tags"
                return $result.status -eq 200 -and $null -ne $result.body.models
            }
            'frontend' {
                $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$FrontendPort/login" -TimeoutSec 3
                return $response.StatusCode -eq 200 -and $response.Content -match '/src/main.js'
            }
        }
    } catch { return $false }
    return $false
}

function Write-State {
    New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    # 不存储环境变量和令牌；进程启动时间用于防止PID被其他程序复用后误停。
    $tempFile = "$stateFile.tmp"
    ConvertTo-Json -InputObject @($script:owned) -Depth 5 |
        Set-Content -LiteralPath $tempFile -Encoding UTF8
    Move-Item -LiteralPath $tempFile -Destination $stateFile -Force
}

function Get-OwnedProcess($Record) {
    # 损坏或嵌套的记录不能用于PID匹配，更不能据此停止进程。
    if ($null -eq $Record -or $Record -is [array] -or
        $Record.pid -is [array] -or $Record.startedAt -is [array] -or
        $Record.executable -is [array]) { return $null }
    $recordPid = 0
    $recordTime = [DateTimeOffset]::MinValue
    if (-not [int]::TryParse([string]$Record.pid, [ref]$recordPid) -or $recordPid -le 0 -or
        [string]::IsNullOrWhiteSpace([string]$Record.executable)) { return $null }
    # PowerShell7可能自动反序列化ISO时间；直接保留其精度，不先转成本地化字符串。
    if ($Record.startedAt -is [DateTimeOffset]) { $recordTime = $Record.startedAt }
    elseif ($Record.startedAt -is [DateTime]) { $recordTime = [DateTimeOffset]$Record.startedAt }
    elseif (-not [DateTimeOffset]::TryParse([string]$Record.startedAt, [ref]$recordTime)) { return $null }
    $process = Get-Process -Id $recordPid -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq $recordTime.UtcDateTime.Ticks -and
        $process.Path -eq $Record.executable) { return $process }
    return $null
}

function Read-ProcessRecords([string]$Path) {
    # Windows PowerShell 5.1的ConvertFrom-Json可能将整个JSON数组作为一个管道对象输出。
    # 先赋值再逐项展开，也兼容旧版重复启动留下的嵌套数组；不要直接@(... | ConvertFrom-Json)。
    $parsed = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($record in $parsed) {
        if ($record -is [array]) {
            # 递归展开的是记录数组，不展开记录属性；身份字段仍须通过Get-OwnedProcess核验。
            Expand-ProcessRecords $record
        } elseif ($null -ne $record) {
            $record
        }
    }
}

function Expand-ProcessRecords($Records) {
    foreach ($record in $Records) {
        if ($record -is [array]) { Expand-ProcessRecords $record }
        elseif ($null -ne $record) { $record }
    }
}

function Stop-Owned($Record) {
    $process = Get-OwnedProcess $Record
    if ($process) {
        Stop-Process -Id $process.Id
        $process.WaitForExit(10000) | Out-Null
        Write-Host "已停止本脚本启动的 $($Record.name)"
    }
    $script:owned = @($script:owned | Where-Object { $_.pid -ne $Record.pid })
    Write-State
}

function Assert-Existing([string]$Name, [int]$Port, [string]$Marker) {
    $listener = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if (-not $listener.Count) { return $false }
    foreach ($connection in $listener) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)"
        $command = ($owner.CommandLine -replace '/', '\')
        if (-not $command -or $command.IndexOf($Marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            throw "$Name 端口 $Port 被其他进程占用；不会停止该进程，请核对后再运行。"
        }
    }
    if (-not (Test-Ready $Name)) {
        throw "$Name 的端口 $Port 已占用但就绪校验失败，请检查原进程配置和日志。"
    }
    Write-Host "复用已就绪的 $Name ($Port)，不接管其停止权限"
    return $true
}

function Start-Managed([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$Directory, [int]$WaitSeconds) {
    $log = Join-Path $runtimeRoot "$Name.stdout.log"
    $errorLog = Join-Path $runtimeRoot "$Name.stderr.log"
    # Start-Process会拼接参数；逐项按Windows原生引号规则编码，支持含空格的项目目录。
    $quoted = @($Arguments | ForEach-Object {
        '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
    }) -join ' '
    $process = Start-Process -FilePath $Executable -ArgumentList $quoted -WorkingDirectory $Directory `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $errorLog
    $record = [pscustomobject]@{
        name = $Name; pid = $process.Id; executable = (Get-Item -LiteralPath $Executable).FullName
        startedAt = $process.StartTime.ToUniversalTime().ToString('o')
    }
    $script:owned += $record
    $script:launched += $record
    Write-State
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) { throw "$Name 启动退出，请查看 $errorLog 和 $log" }
        if (Test-Ready $Name) { Write-Host "$Name 已就绪"; return }
        Start-Sleep -Milliseconds 400
    }
    throw "$Name 启动超时，请查看 $errorLog 和 $log"
}

try {
    # 同一项目的并行启动/停止串行执行，避免重复拉起或覆盖进程清单。
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $digest = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRoot))).Replace('-', '') }
    finally { $hasher.Dispose() }
    $mutex = New-Object Threading.Mutex($false, "Local\AI-library-$digest")
    try { $hasMutex = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $hasMutex = $true }
    if (-not $hasMutex) { throw '本项目已有启动/停止脚本正在执行，请等待完成。' }
    if (Test-Path -LiteralPath $stateFile) { $script:owned = @(Read-ProcessRecords $stateFile) }

    if ($Action -eq 'Stop') {
        $records = @($script:owned)
        [array]::Reverse($records)
        foreach ($record in $records) { Stop-Owned $record }
        Write-Host '已停止本脚本管理的服务；IDEA、已复用服务和Docker容器保持运行。'
        return
    }

    Import-LocalConfiguration
    if (@($BackendPort,$AiPort,$FrontendPort,$OllamaPort | Select-Object -Unique).Count -ne 4) {
        throw '后端、AI、前端与Ollama端口必须各不相同。'
    }
    if (-not $SkipOllama -and $OllamaPort -eq 11434) {
        throw '11434保留给用户默认模型服务；请使用独立11435实例。'
    }
    if ($Action -eq 'Status') {
        foreach ($entry in @(@('ai',$AiPort),@('backend',$BackendPort),@('frontend',$FrontendPort),@('ollama',$OllamaPort))) {
            $managed = @($script:owned | Where-Object { $_.name -eq $entry[0] -and (Get-OwnedProcess $_) }).Count -gt 0
            [pscustomobject]@{ Service=$entry[0]; Port=$entry[1]; Ready=(Test-Ready $entry[0]); Managed=$managed }
        }
        return
    }

    # 清除已退出的旧记录，但不接管用户手动启动的现有服务。
    $script:owned = @($script:owned | Where-Object { Get-OwnedProcess $_ })
    New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
    if (-not $PythonPath) { $PythonPath = $env:AI_PYTHON }
    if (-not $PythonPath) { $PythonPath = Join-Path $env:USERPROFILE 'miniconda3\envs\torch312\python.exe' }
    if (-not (Test-Path -LiteralPath $PythonPath)) { throw '未找到torch312 Python，请使用 -PythonPath 指定解释器。' }
    if (-not $JavaPath -and $env:JAVA_HOME) { $JavaPath = Join-Path $env:JAVA_HOME 'bin\java.exe' }
    if (-not $JavaPath) { $JavaPath = (Get-Command java.exe -ErrorAction Stop).Source }

    if (-not $env:RECOMMENDATION_AI_TOKEN) {
        $tokenFile = Join-Path $aiRoot 'data\rerank-token.txt'
        if (Test-Path -LiteralPath $tokenFile) { Set-ChildEnvironment 'RECOMMENDATION_AI_TOKEN' (Get-Content $tokenFile -Raw).Trim() }
    }
    if (-not $env:RECOMMENDATION_AI_TOKEN -or $env:RECOMMENDATION_AI_TOKEN.Length -lt 24) {
        throw '请配置Java和Python共享的RECOMMENDATION_AI_TOKEN，至少24位。'
    }
    Set-ChildEnvironment 'LIBRARY_API_URL' "http://127.0.0.1:$BackendPort"
    Set-ChildEnvironment 'LIBRARY_AI_URL' "http://127.0.0.1:$AiPort"
    Set-ChildEnvironment 'RECOMMENDATION_AI_URL' "http://127.0.0.1:$AiPort/internal/recommendations/rerank"
    Set-ChildEnvironment 'RECOMMENDATION_AI_PORT' "$AiPort"
    Set-ChildEnvironment 'OLLAMA_BASE_URL' "http://127.0.0.1:$OllamaPort"
    Set-ChildEnvironment 'OLLAMA_CONTEXT_LENGTH' '32768'
    Set-ChildEnvironment 'PYTHONUTF8' '1'
    Set-ChildEnvironment 'PYTHONUNBUFFERED' '1'
    Set-ChildEnvironment 'ANONYMIZED_TELEMETRY' 'False'
    # 本机迁移后显式使用新索引和清单，避免静默读取旧目录或历史演示知识。
    Set-ChildEnvironment 'AI_KNOWLEDGE_DIR' (Join-Path $aiRoot 'data\library-guide')
    Set-ChildEnvironment 'RAG_DATA_DIR' $env:AI_KNOWLEDGE_DIR
    Set-ChildEnvironment 'AI_KNOWLEDGE_MANIFEST' (Join-Path $aiRoot 'knowledge\library-guide\manifest.json')

    $mysqlHost, $mysqlPort = '127.0.0.1', 3306
    if ($env:MYSQL_URL) {
        $mysqlUri = [uri]($env:MYSQL_URL -replace '^jdbc:', '')
        $mysqlHost = $mysqlUri.Host
        $mysqlPort = if ($mysqlUri.Port -gt 0) { $mysqlUri.Port } else { 3306 }
    }
    foreach ($dependency in @(
        @('MySQL', $mysqlHost, $mysqlPort),
        @('Redis', $(if ($env:REDIS_HOST) { $env:REDIS_HOST } else { '127.0.0.1' }), $(if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 })),
        @('RabbitMQ', $(if ($env:RABBITMQ_HOST) { $env:RABBITMQ_HOST } else { '127.0.0.1' }), $(if ($env:RABBITMQ_PORT) { [int]$env:RABBITMQ_PORT } else { 25673 }))
    )) {
        if (-not (Test-Tcp $dependency[1] $dependency[2])) { throw "$($dependency[0]) 未连接，请启动已有服务并核对配置。" }
        Write-Host "$($dependency[0]) 连接可达"
    }
    # Windows PowerShell 5.1会剥离-c中的双引号；使用文件入口并单独重定向原始stderr。
    # 不通过2>&1管道接收原生命令，避免ErrorActionPreference=Stop将错误提前截断。
    $preflightLog = Join-Path $runtimeRoot 'python-preflight.log'
    $preflightErrorLog = Join-Path $runtimeRoot 'python-preflight.stderr.log'
    $preflightFile = Join-Path $aiRoot 'preflight.py'
    $preflight = Start-Process -FilePath $PythonPath -ArgumentList ('"' + $preflightFile + '"') `
        -WorkingDirectory $aiRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $preflightLog -RedirectStandardError $preflightErrorLog
    # 先取得句柄，兼容Windows PowerShell 5.1退出后读取ExitCode的行为。
    $null = $preflight.Handle
    if (-not $preflight.WaitForExit(30000)) {
        $preflight.Kill()
        $preflight.WaitForExit(5000) | Out-Null
        throw 'Python依赖检测超过30秒，请查看target/local-runtime/python-preflight.stderr.log。'
    }
    $preflight.Refresh()
    if ($null -eq $preflight.ExitCode -or $preflight.ExitCode -ne 0) {
        throw 'Python依赖检测失败，请查看target/local-runtime/python-preflight.stderr.log。'
    }
    if ($Action -eq 'Check') { Write-Host '配置和依赖预检查通过；未启动或停止服务。'; return }

    if (-not $SkipOllama -and -not (Assert-Existing 'ollama' $OllamaPort 'ollama.exe')) {
        $ollama = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
        if (-not (Test-Path -LiteralPath $ollama)) { throw '未找到Ollama，云端专用运行可加 -SkipOllama。' }
        # 不访问用户默认11434/CUDA实例。Vulkan0是本机已验收Intel核显。
        Set-ChildEnvironment 'CUDA_VISIBLE_DEVICES' '-1'
        Set-ChildEnvironment 'ROCR_VISIBLE_DEVICES' '-1'
        Set-ChildEnvironment 'GGML_VK_VISIBLE_DEVICES' '0'
        Set-ChildEnvironment 'OLLAMA_VULKAN' '1'
        Set-ChildEnvironment 'OLLAMA_IGPU_ENABLE' '1'
        Set-ChildEnvironment 'OLLAMA_HOST' "127.0.0.1:$OllamaPort"
        Start-Managed 'ollama' $ollama @('serve') $aiRoot 30
    }
    if (-not (Assert-Existing 'ai' $AiPort $aiRoot)) {
        Start-Managed 'ai' $PythonPath @((Join-Path $aiRoot 'ai_server.py')) $aiRoot 45
    }
    if (-not $SkipBackend -and -not (Assert-Existing 'backend' $BackendPort $projectRoot)) {
        if ($Build) {
            Push-Location $projectRoot
            try { & .\mvnw.cmd '-DskipTests' 'package' *> (Join-Path $runtimeRoot 'backend-build.log') }
            finally { Pop-Location }
            if ($LASTEXITCODE -ne 0) { throw 'Java打包失败，请查看backend-build.log。' }
        }
        $jar = Join-Path $projectRoot 'target\AI-library-1.0.0.jar'
        if (-not (Test-Path -LiteralPath $jar)) { throw '缺少Java JAR，请先打包或加 -Build。' }
        Start-Managed 'backend' $JavaPath @('-jar',$jar,"--server.port=$BackendPort","--library.ai.business-base-url=http://127.0.0.1:$BackendPort") $projectRoot 90
    }
    if (-not $SkipFrontend -and -not (Assert-Existing 'frontend' $FrontendPort (Join-Path $projectRoot 'frontend'))) {
        $node = (Get-Command node.exe -ErrorAction Stop).Source
        $vite = Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js'
        if (-not (Test-Path -LiteralPath $vite)) { throw '请先在frontend执行npm ci。' }
        Set-ChildEnvironment 'VITE_BACKEND_URL' "http://127.0.0.1:$BackendPort"
        Start-Managed 'frontend' $node @($vite,'--host','127.0.0.1','--port',"$FrontendPort",'--strictPort') (Join-Path $projectRoot 'frontend') 30
    }
    Write-Host "启动完成：AI http://127.0.0.1:$AiPort，后端 http://127.0.0.1:$BackendPort"
    if (-not $SkipFrontend) { Write-Host "前端 http://127.0.0.1:$FrontendPort" }
    Write-Host '日志：target/local-runtime；停止：.\start.ps1 -Action Stop'
} catch {
    # 本次失败仅回滚本次新建进程；先前已运行和外部复用的服务不受影响。
    $records = @($script:launched)
    [array]::Reverse($records)
    foreach ($record in $records) { Stop-Owned $record }
    throw
} finally {
    foreach ($key in $script:originalEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $script:originalEnvironment[$key], 'Process')
    }
    if ($hasMutex) { $mutex.ReleaseMutex() }
    if ($mutex) { $mutex.Dispose() }
}
