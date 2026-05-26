# =================================================================
# 把本地开发环境的政策文件 + 办公模板一键同步到服务器
# =================================================================
# 做的事:
#   1) 从本地 PG (college-pgvector-dev) 抽出所有 qa_document 行
#      (含 file_path 非空的 = 有真实文件的)
#   2) 对应的 backend/uploads/... 文件打 tar
#   3) scp tar + SQL 到服务器
#   4) 服务器上: docker cp tar 进 backend 容器解开, psql 执行 INSERT
#   5) 已有同 file_path 的行先 DELETE 再 INSERT, 幂等可重跑
#
# 用法 (项目根目录):
#   .\scripts\seed-server-docs.ps1                                          # 默认
#   .\scripts\seed-server-docs.ps1 -Server user@10.10.0.27
#   .\scripts\seed-server-docs.ps1 -DryRun                                  # 只生成本地 sql/tar, 不上传
#
# 前置:
#   - 本地 docker 跑着 college-pgvector-dev 容器 (开发库)
#   - 服务器已部署完成 (college-service-backend-1 / college-service-postgres-1 运行中)
#   - 本地 ssh / scp 能免密登录服务器, 或愿意每条命令输一次密码
# =================================================================

[CmdletBinding()]
param(
    [string]$Server = 'user@10.10.0.27',
    [string]$LocalDbContainer = 'college-pgvector-dev',
    [string]$ServerBackendContainer = 'college-service-backend-1',
    [string]$ServerPostgresContainer = 'college-service-postgres-1',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackendDir  = Join-Path $ProjectRoot 'backend'

# Windows PowerShell 5.1 默认按 GBK 抓 docker exec 的 stdout, 中文路径会乱码.
# 把 console 输入输出都强制为 UTF-8, 才能正确读 PG 返回的 file_path.
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)

function Step($t) { Write-Host ""; Write-Host "=== $t ===" -ForegroundColor Cyan }
function Ok($t)   { Write-Host "  [OK] $t"   -ForegroundColor Green }
function Skip($t) { Write-Host "  [SKIP] $t" -ForegroundColor DarkGray }

# ---------- 0) 前置检查 ----------
Step '0/5 前置检查'
foreach ($cmd in @('docker','tar','scp','ssh')) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "$cmd 未安装或不在 PATH (Windows 10 1803+ 内建 tar)"
    }
}
docker exec $LocalDbContainer psql -U postgres -d college_service -c 'SELECT 1' | Out-Null
Ok "本地 DB 容器 $LocalDbContainer 可连"

# ---------- 1) 从本地 DB 抽 SQL ----------
Step '1/5 抽取本地 qa_document 行'

# 用 PG format() 函数生成已经转义好的 VALUES 元组, 避免 PowerShell 拼字符串时被中文/引号坑
$valuesQuery = @"
SELECT format(
  E'(%L, %L, %L, %L, %s, %L, %L, 1, 0)',
  title, category, doc_type, file_path, file_size, file_type, COALESCE(description, '')
)
FROM qa_document
WHERE file_path IS NOT NULL AND file_path <> ''
ORDER BY id;
"@
$valuesLines = docker exec $LocalDbContainer psql -U postgres -d college_service -t -A -c $valuesQuery
$valuesLines = $valuesLines | Where-Object { $_ -and $_.Trim() }

if (-not $valuesLines) { throw '本地 DB qa_document 表无带文件记录, 没什么可同步的' }

# 文件路径列表
$pathLines = docker exec $LocalDbContainer psql -U postgres -d college_service -t -A -c `
    "SELECT file_path FROM qa_document WHERE file_path IS NOT NULL AND file_path <> '' ORDER BY id;"
$pathLines = $pathLines | Where-Object { $_ -and $_.Trim() }

Write-Host "  共 $($valuesLines.Count) 条 (含政策文件 + 模板):"
$pathLines | ForEach-Object { Write-Host "    - $_" }

# ---------- 2) 检查本地文件存在 ----------
Step '2/5 校验本地文件'
$missing = @()
foreach ($p in $pathLines) {
    $f = Join-Path $BackendDir $p
    if (-not (Test-Path $f)) { $missing += $p } else { Ok $p }
}
if ($missing.Count -gt 0) {
    Write-Host "  以下文件本地缺失, 跳过它们的 INSERT:" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "    - $_" -ForegroundColor Yellow }
    # 过滤掉缺失的
    $keepIdx = 0..($pathLines.Count - 1) | Where-Object { $pathLines[$_] -notin $missing }
    $pathLines   = $keepIdx | ForEach-Object { $pathLines[$_] }
    $valuesLines = $keepIdx | ForEach-Object { $valuesLines[$_] }
    if (-not $pathLines) { throw '所有文件都缺失, 退出' }
}

# ---------- 3) 生成 SQL + tar ----------
Step '3/5 打包文件 + 拼装 SQL'

$workDir = Join-Path $env:TEMP "college-seed-$(Get-Date -Format yyyyMMddHHmmss)"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

$sqlPath = Join-Path $workDir 'seed-docs.sql'
$tarPath = Join-Path $workDir 'seed-docs.tar'

# DELETE 冲突项 + INSERT
$inClause = ($pathLines | ForEach-Object { "'" + $_.Replace("'","''") + "'" }) -join ','
$valuesClause = $valuesLines -join ",`n  "
$sqlText = @"
-- 由 scripts/seed-server-docs.ps1 生成
-- 幂等: 同 file_path 的旧行先 DELETE 再 INSERT
BEGIN;

DELETE FROM qa_document WHERE file_path IN ($inClause);

INSERT INTO qa_document (title, category, doc_type, file_path, file_size, file_type, description, status, download_count) VALUES
  $valuesClause;

SELECT id, title, doc_type, file_path FROM qa_document WHERE file_path IN ($inClause) ORDER BY id;

COMMIT;
"@
[System.IO.File]::WriteAllText($sqlPath, $sqlText, [System.Text.UTF8Encoding]::new($false))
Ok "SQL: $sqlPath ($($sqlText.Length) 字节)"

# tar (在 backend 目录下打包, 保留 uploads/yyyy-mm-dd/... 与 uploads/templates/... 路径)
Push-Location $BackendDir
try {
    & tar -cf $tarPath $pathLines
    if ($LASTEXITCODE -ne 0) { throw "tar 失败" }
} finally { Pop-Location }
$tarSize = (Get-Item $tarPath).Length
Ok "tar: $tarPath ($([math]::Round($tarSize/1KB,1)) KB)"

if ($DryRun) {
    Step 'DryRun 模式'
    Write-Host "  SQL: $sqlPath"
    Write-Host "  tar: $tarPath"
    Write-Host "  未上传服务器. 你可以手动检查这两个文件再决定."
    return
}

# ---------- 4) scp 上传 ----------
Step '4/5 上传到服务器'
& scp $tarPath "${Server}:/tmp/seed-docs.tar"
if ($LASTEXITCODE -ne 0) { throw "scp tar 失败" }
& scp $sqlPath "${Server}:/tmp/seed-docs.sql"
if ($LASTEXITCODE -ne 0) { throw "scp sql 失败" }
Ok '两份文件已 scp'

# ---------- 5) 服务器上执行 ----------
Step '5/5 服务器上 docker cp + psql'

$remoteCmd = @"
set -e
sudo docker exec $ServerBackendContainer mkdir -p /app/uploads
sudo docker cp /tmp/seed-docs.tar ${ServerBackendContainer}:/tmp/seed-docs.tar
sudo docker exec $ServerBackendContainer tar -xf /tmp/seed-docs.tar -C /app/
sudo docker exec $ServerBackendContainer rm /tmp/seed-docs.tar
sudo docker cp /tmp/seed-docs.sql ${ServerPostgresContainer}:/tmp/seed-docs.sql
sudo docker exec $ServerPostgresContainer psql -U postgres -d college_service -f /tmp/seed-docs.sql
sudo docker exec $ServerPostgresContainer rm /tmp/seed-docs.sql
rm /tmp/seed-docs.tar /tmp/seed-docs.sql
echo '--- backend 容器 uploads/ 目录 ---'
sudo docker exec $ServerBackendContainer ls -R /app/uploads
"@
& ssh -t $Server $remoteCmd
if ($LASTEXITCODE -ne 0) { throw 'ssh 远程执行失败' }

Step '完成'
Write-Host "  服务器 qa_document 已落库, backend 容器 uploads/ 已落盘" -ForegroundColor Green
Write-Host "  RAG 检索需要管理端 -> 政策文档 -> 对每个 PDF 点 [重新索引] 一次" -ForegroundColor Yellow
