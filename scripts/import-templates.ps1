# =================================================================
# 导入党团办公模板到 qa_document 表的占位记录
# =================================================================
# 用途:
#   把 D:\Vscode\software\project\党团文件\ 下的模板 docx 拷贝到 backend/uploads/templates/,
#   并 UPDATE 已经预置的占位记录 (file_path 为空那条) 让它们对外可下载.
#
# 用法 (项目根目录):
#   pwsh scripts/import-templates.ps1                       # 默认 PG 5433, 库 college_service
#   pwsh scripts/import-templates.ps1 -DbPort 5432 -DbName mydb
#
# 前置:
#   - 已执行 schema.sql (新部署) 或 deploy/sql/qa_template_migrate.sql (已有库)
#   - psql.exe 在 PATH, 或修改下方 $psql 变量
#   - PostgreSQL 密码可由 $env:PGPASSWORD 覆盖, 默认 postgres
# =================================================================

[CmdletBinding()]
param(
    [string]$DbHost = 'localhost',
    [int]$DbPort = 5433,
    [string]$DbName = 'college_service',
    [string]$DbUser = 'postgres'
)

$ErrorActionPreference = 'Stop'

# ---------- 路径 ----------
$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$SourceDir   = 'D:\Vscode\software\project\党团文件'
$TargetRoot  = Join-Path $ProjectRoot 'backend\uploads\templates'

# psql 路径自检
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $candidates = @(
        'C:\Program Files\PostgreSQL\16\bin\psql.exe',
        'C:\Program Files\PostgreSQL\15\bin\psql.exe',
        'C:\Program Files\PostgreSQL\14\bin\psql.exe'
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $psql = $c; break }
    }
}
if (-not $psql) { throw 'psql.exe 未找到, 请安装 PostgreSQL 客户端或加入 PATH' }

if (-not $env:PGPASSWORD) { $env:PGPASSWORD = 'postgres' }

# ---------- 待导入模板清单 ----------
# 每条: title(=qa_document.title), srcFile, contentType
$Templates = @(
    @{ Title = '党员证明模板'; Src = '党员证明模板.docx'; Type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
    @{ Title = '团员证明模板'; Src = '团员证明模板.docx'; Type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
    # 下面三条暂无源文件, 留作待补
    # @{ Title = '请假条模板';   Src = '请假条模板.docx';   Type = '...' }
    # @{ Title = '活动预算表模板'; Src = '活动预算表.xlsx'; Type = '...' }
    # @{ Title = '班会简报模板'; Src = '班会简报模板.docx'; Type = '...' }
)

# ---------- 创建目标目录 ----------
if (-not (Test-Path $TargetRoot)) {
    New-Item -ItemType Directory -Path $TargetRoot -Force | Out-Null
}

Write-Host ""
Write-Host '=== 学院综合服务平台 - 模板导入 ===' -ForegroundColor Cyan
Write-Host "  源目录: $SourceDir"
Write-Host "  目的:   $TargetRoot"
Write-Host "  数据库: $DbUser@${DbHost}:$DbPort/$DbName"
Write-Host ''

# ---------- 1) 拷贝文件 + 收集 path/size 信息 ----------
$Records = @()
foreach ($t in $Templates) {
    $src = Join-Path $SourceDir $t.Src
    if (-not (Test-Path $src)) {
        Write-Warning "  [SKIP] 源文件不存在: $src"
        continue
    }
    $dest = Join-Path $TargetRoot $t.Src
    Copy-Item $src $dest -Force
    $size = (Get-Item $dest).Length
    # file_path 相对后端启动目录 (即 backend/), 与现有 uploads/yyyy-MM-dd/... 风格保持一致
    $relPath = 'uploads/templates/' + $t.Src
    Write-Host ("  [OK] {0,-18} {1,8} bytes -> {2}" -f $t.Title, $size, $relPath) -ForegroundColor Green
    $Records += @{
        Title    = $t.Title
        FilePath = $relPath
        FileSize = $size
        FileType = $t.Type
    }
}

if ($Records.Count -eq 0) {
    Write-Host '[INFO] 没有可导入的模板' -ForegroundColor Yellow
    return
}

# ---------- 2) 生成 UPDATE SQL ----------
# 优先匹配同名占位记录 (file_path 为空), 若没有就 INSERT 新行
$sql = New-Object System.Text.StringBuilder
[void]$sql.AppendLine('BEGIN;')
foreach ($r in $Records) {
    $titleEsc = $r.Title.Replace("'", "''")
    $pathEsc  = $r.FilePath.Replace("'", "''")
    $typeEsc  = $r.FileType.Replace("'", "''")
    [void]$sql.AppendLine(@"
DO `$`$
DECLARE row_id BIGINT;
BEGIN
  SELECT id INTO row_id FROM qa_document
  WHERE doc_type = 'template' AND title = '$titleEsc' AND (file_path IS NULL OR file_path = '')
  LIMIT 1;
  IF row_id IS NOT NULL THEN
    UPDATE qa_document
       SET file_path = '$pathEsc',
           file_size = $($r.FileSize),
           file_type = '$typeEsc'
     WHERE id = row_id;
    RAISE NOTICE '更新占位模板: % -> %', '$titleEsc', '$pathEsc';
  ELSE
    INSERT INTO qa_document (title, category, doc_type, file_path, file_size, file_type, description)
    VALUES ('$titleEsc', '党团证明', 'template', '$pathEsc', $($r.FileSize), '$typeEsc', '由 import-templates.ps1 自动导入');
    RAISE NOTICE '新增模板: %', '$titleEsc';
  END IF;
END
`$`$;
"@)
}
[void]$sql.AppendLine('COMMIT;')

$sqlPath = Join-Path $env:TEMP "import-templates-$(Get-Date -Format yyyyMMddHHmmss).sql"
$sql.ToString() | Out-File -Encoding utf8 $sqlPath

# ---------- 3) 执行 SQL ----------
Write-Host ''
Write-Host '=== 写入数据库 ===' -ForegroundColor Cyan
& $psql -U $DbUser -h $DbHost -p $DbPort -d $DbName -f $sqlPath
if ($LASTEXITCODE -ne 0) { throw 'psql 执行失败, 见上方错误' }

Remove-Item $sqlPath

Write-Host ''
Write-Host '=== 完成 ===' -ForegroundColor Green
Write-Host '管理员访问管理端 -> 智能问答 -> 办公模板 即可看到刚导入的模板'
Write-Host '占位记录 (请假条/活动预算/班会简报) 可在管理端通过"补传文件"按钮上传'
