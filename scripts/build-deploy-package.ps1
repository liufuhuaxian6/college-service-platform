# =================================================================
# 学院综合服务平台 - 本地构建并打包离线部署包
# =================================================================
# 在开发电脑（Windows + Docker Desktop）上运行。
# 产出 deploy-package/ 目录，可直接 scp 到服务器，配合服务器端
# scripts/deploy.sh 完成离线部署。
#
# 用法:
#   pwsh scripts/build-deploy-package.ps1                    # 增量打包
#   pwsh scripts/build-deploy-package.ps1 -Force             # 强制重建所有镜像 tar 和模型
#   pwsh scripts/build-deploy-package.ps1 -SkipFrontend      # 跳过前端构建
#
# 输出:
#   deploy-package/
#     images/               # 5 个 docker save 出的 tar
#     models/               # BGE 模型
#     admin/                # 管理端 dist
#     docker-compose.yml
#     .env
#     nginx.conf
#     schema.sql
#     deploy.sh             # 服务器端执行脚本
# =================================================================

[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$SkipFrontend,
    [switch]$SkipBackend
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ---------- 路径 ----------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$DeployPkg = Join-Path $ProjectRoot 'deploy-package'
$ImagesDir = Join-Path $DeployPkg 'images'
$ModelsDir = Join-Path $ProjectRoot 'models'
$BgeDir = Join-Path $ModelsDir 'bge-small-zh-v1.5'

Set-Location $ProjectRoot

function Step($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Ok($msg) {
    Write-Host "  [OK] $msg" -ForegroundColor Green
}

function Skip($msg) {
    Write-Host "  [SKIP] $msg" -ForegroundColor DarkGray
}

# ---------- 0) 前置检查 ----------
Step '0/7 环境检查'
foreach ($cmd in @('docker', 'curl.exe')) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "$cmd 未安装或不在 PATH 中"
    }
}
docker info | Out-Null
Ok 'docker / curl 已就绪'

# ---------- 1) 后端 JAR ----------
Step '1/7 构建后端 JAR'
if ($SkipBackend) { Skip '按参数跳过' } else {
    Push-Location 'backend'
    try {
        & .\mvnw.cmd -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'mvnw package 失败' }
        Ok 'target/college-service-platform-1.0.0-SNAPSHOT.jar'
    } finally { Pop-Location }
}

# ---------- 2) 后端 Docker 镜像 ----------
Step '2/7 构建并保存后端镜像'
$backendTar = Join-Path $ImagesDir 'college-backend-1.0.0.tar'
New-Item -ItemType Directory -Path $ImagesDir -Force | Out-Null
if (-not $SkipBackend -or $Force -or -not (Test-Path $backendTar)) {
    docker build -t college-backend:1.0.0 -f backend/Dockerfile.prod backend
    if ($LASTEXITCODE -ne 0) { throw 'docker build 失败' }
    docker save college-backend:1.0.0 -o $backendTar
    Ok "$backendTar"
} else { Skip 'tar 已存在, 使用 -Force 强制重建' }

# ---------- 3) 基础镜像 (5 个) ----------
Step '3/7 拉取并保存基础镜像'
$baseImages = @{
    'pgvector/pgvector:pg16'                                                 = 'pgvector-pg16.tar'
    'redis:7-alpine'                                                         = 'redis-7-alpine.tar'
    'nginx:alpine'                                                           = 'nginx-alpine.tar'
    'ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5'          = 'tei-cpu-1.5.tar'
}
foreach ($img in $baseImages.Keys) {
    $tar = Join-Path $ImagesDir $baseImages[$img]
    if ((Test-Path $tar) -and -not $Force) {
        Skip "$($baseImages[$img]) 已存在"
        continue
    }
    Write-Host "  拉取 $img ..."
    docker pull $img
    if ($LASTEXITCODE -ne 0) { throw "docker pull $img 失败" }
    docker save $img -o $tar
    Ok $baseImages[$img]
}

# ---------- 4) BGE 模型 ----------
Step '4/7 准备 BGE-small-zh-v1.5 模型'
New-Item -ItemType Directory -Path "$BgeDir\onnx", "$BgeDir\1_Pooling" -Force | Out-Null

$baseUrl = 'https://hf-mirror.com/BAAI/bge-small-zh-v1.5/resolve/main'
$xenovaUrl = 'https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main'

$modelFiles = @(
    @{ Url = "$baseUrl/config.json"; File = 'config.json' }
    @{ Url = "$baseUrl/tokenizer.json"; File = 'tokenizer.json' }
    @{ Url = "$baseUrl/tokenizer_config.json"; File = 'tokenizer_config.json' }
    @{ Url = "$baseUrl/special_tokens_map.json"; File = 'special_tokens_map.json' }
    @{ Url = "$baseUrl/vocab.txt"; File = 'vocab.txt' }
    @{ Url = "$baseUrl/modules.json"; File = 'modules.json' }
    @{ Url = "$baseUrl/config_sentence_transformers.json"; File = 'config_sentence_transformers.json' }
    @{ Url = "$baseUrl/sentence_bert_config.json"; File = 'sentence_bert_config.json' }
    @{ Url = "$baseUrl/1_Pooling/config.json"; File = '1_Pooling/config.json' }
    @{ Url = "$xenovaUrl/onnx/model.onnx"; File = 'onnx/model.onnx' }
)

foreach ($m in $modelFiles) {
    $dest = Join-Path $BgeDir $m.File
    if ((Test-Path $dest) -and (Get-Item $dest).Length -gt 50 -and -not $Force) {
        Skip $m.File
        continue
    }
    Write-Host "  下载 $($m.File) ..."
    curl.exe -fSL --retry 5 --retry-delay 2 -o "$dest" $m.Url
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $dest)) {
        throw "下载失败: $($m.Url)"
    }
    Ok $m.File
}

# ---------- 5) 前端 ----------
Step '5/7 构建管理端前端'
if ($SkipFrontend) { Skip '按参数跳过' } else {
    Push-Location 'frontend-admin'
    try {
        if (-not (Test-Path 'node_modules')) {
            Write-Host '  首次安装依赖 (npm install) ...'
            & npm install --silent
            if ($LASTEXITCODE -ne 0) { throw 'npm install 失败' }
        }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'npm run build 失败' }
        Ok 'frontend-admin/dist/ 已生成'
    } finally { Pop-Location }
}

# ---------- 6) 装配部署包 ----------
Step '6/7 装配 deploy-package/'

# 先清旧（保留 images/ 以复用缓存）
Get-ChildItem $DeployPkg -Exclude 'images' -ErrorAction SilentlyContinue |
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# 前端 dist
$adminDest = Join-Path $DeployPkg 'admin'
if (-not $SkipFrontend) {
    Copy-Item -Recurse -Force 'frontend-admin/dist' $adminDest
    Ok 'admin/'
}

# 模型: 保持部署包内目录为 models/bge-small-zh-v1.5/
# TEI 容器的启动参数固定读取 /models/bge-small-zh-v1.5/config.json。
$modelsRoot = Join-Path $DeployPkg 'models'
$bgeDest = Join-Path $modelsRoot 'bge-small-zh-v1.5'
New-Item -ItemType Directory -Force -Path $bgeDest | Out-Null
Copy-Item -Recurse -Force (Join-Path $BgeDir '*') $bgeDest
if (-not (Test-Path (Join-Path $bgeDest 'config.json'))) {
    throw '模型装配失败: deploy-package/models/bge-small-zh-v1.5/config.json 不存在'
}
if (-not (Test-Path (Join-Path $bgeDest 'onnx/model.onnx'))) {
    throw '模型装配失败: deploy-package/models/bge-small-zh-v1.5/onnx/model.onnx 不存在'
}
Ok 'models/bge-small-zh-v1.5/'

# 配置文件
Copy-Item 'deploy/docker-compose.prod.yml' (Join-Path $DeployPkg 'docker-compose.yml')
Copy-Item 'deploy/nginx.conf' $DeployPkg
Copy-Item 'deploy/sql/schema.sql' $DeployPkg
Copy-Item 'scripts/deploy.sh' $DeployPkg
# .env.prod (如有)
if (Test-Path 'deploy/.env.prod') {
    Copy-Item 'deploy/.env.prod' (Join-Path $DeployPkg '.env')
} else {
    @"
DB_NAME=college_service
DB_USER=postgres
DB_PASSWORD=postgres
REDIS_PASSWORD=
JWT_SECRET=ChangeThisJwtSecretBefore生产部署MustBe32BytesAtLeast
"@ | Out-File -Encoding utf8 (Join-Path $DeployPkg '.env')
    Write-Host '  [WARN] 用了默认 .env, 部署前请改 JWT_SECRET 和 DB_PASSWORD' -ForegroundColor Yellow
}
Ok '配置文件 + deploy.sh'

# ---------- 7) 摘要 ----------
Step '7/7 完成'
$total = (Get-ChildItem $DeployPkg -Recurse | Measure-Object Length -Sum).Sum
$totalMB = [Math]::Round($total / 1MB, 1)
Write-Host "  deploy-package/ 总大小: ${totalMB} MB"
Get-ChildItem $DeployPkg | ForEach-Object {
    $size = if ($_.PSIsContainer) {
        [Math]::Round(((Get-ChildItem $_.FullName -Recurse | Measure-Object Length -Sum).Sum / 1MB), 1)
    } else {
        [Math]::Round(($_.Length / 1MB), 1)
    }
    "    {0,-30} {1,8} MB" -f $_.Name, $size
}

Write-Host ""
Write-Host "下一步：传到服务器" -ForegroundColor Green
Write-Host "  scp -r deploy-package user@10.10.0.27:~/" -ForegroundColor Green
Write-Host "  ssh user@10.10.0.27 'cd ~/deploy-package && bash deploy.sh'" -ForegroundColor Green
