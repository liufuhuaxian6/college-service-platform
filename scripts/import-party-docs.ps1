param(
  [string]$BaseUrl = "http://localhost:8080/api",
  [string]$DocsDir = "D:\Vscode\software\project\党团文件",
  [string]$StudentId = "admin",
  [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"

function Get-Category([string]$fileName) {
  $partyFlow = "$([char]0x515A)$([char]0x56E2)$([char]0x6D41)$([char]0x7A0B)"
  $calendar = "$([char]0x6821)$([char]0x5386)$([char]0x5B89)$([char]0x6392)"
  $studentStatus = "$([char]0x5B66)$([char]0x7C4D)$([char]0x7BA1)$([char]0x7406)"
  $discipline = "$([char]0x7EAA)$([char]0x5F8B)$([char]0x5904)$([char]0x5206)"
  $other = "$([char]0x5176)$([char]0x4ED6)"

  if ($fileName -like "*$([char]0x53D1)$([char]0x5C55)$([char]0x515A)$([char]0x5458)*") { return $partyFlow }
  if ($fileName -like "*$([char]0x6821)$([char]0x5386)*") { return $calendar }
  if ($fileName -like "*$([char]0x5B66)$([char]0x7C4D)*") { return $studentStatus }
  if ($fileName -like "*$([char]0x8FDD)$([char]0x7EAA)*" -or $fileName -like "*$([char]0x5904)$([char]0x5206)*") { return $discipline }
  return $other
}

function ConvertTo-JsonString([string]$value) {
  if ($null -eq $value) { return '""' }
  $sb = New-Object System.Text.StringBuilder
  [void]$sb.Append('"')
  foreach ($ch in $value.ToCharArray()) {
    $code = [int][char]$ch
    switch ($ch) {
      '"' { [void]$sb.Append('\"'); continue }
      '\' { [void]$sb.Append('\\'); continue }
      "`b" { [void]$sb.Append('\b'); continue }
      "`f" { [void]$sb.Append('\f'); continue }
      "`n" { [void]$sb.Append('\n'); continue }
      "`r" { [void]$sb.Append('\r'); continue }
      "`t" { [void]$sb.Append('\t'); continue }
    }
    if ($code -lt 32 -or $code -gt 126) {
      [void]$sb.Append('\u')
      [void]$sb.Append($code.ToString('x4'))
    } else {
      [void]$sb.Append($ch)
    }
  }
  [void]$sb.Append('"')
  return $sb.ToString()
}

function New-DocumentJson($title, $category, $fileInfo) {
  return @(
    "{"
    "  `"title`": $(ConvertTo-JsonString $title),"
    "  `"category`": $(ConvertTo-JsonString $category),"
    "  `"fileName`": $(ConvertTo-JsonString $fileInfo.fileName),"
    "  `"filePath`": $(ConvertTo-JsonString $fileInfo.filePath),"
    "  `"fileSize`": $($fileInfo.fileSize),"
    "  `"fileType`": $(ConvertTo-JsonString $fileInfo.fileType)"
    "}"
  ) -join "`n"
}

if (-not (Test-Path -LiteralPath $DocsDir)) {
  throw "DocsDir not found: $DocsDir"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$loginBody = @{
  studentId = $StudentId
  password = $Password
} | ConvertTo-Json

Write-Host "Login: $StudentId -> $BaseUrl"
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

if ($login.code -ne 200 -or -not $login.data.token) {
  throw "Login failed: $($login.message)"
}

$headers = @{
  Authorization = "Bearer $($login.data.token)"
}

$files = Get-ChildItem -LiteralPath $DocsDir -File |
  Where-Object { $_.Extension.ToLowerInvariant() -in @(".pdf", ".png", ".jpg", ".jpeg") } |
  Sort-Object Name

if ($files.Count -eq 0) {
  throw "No supported files found in $DocsDir"
}

foreach ($file in $files) {
  $title = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
  $category = Get-Category $file.Name

  Write-Host "Upload: $($file.Name) [$category]"
  $uploadRaw = & curl.exe -s `
    -X POST `
    -H "Authorization: Bearer $($login.data.token)" `
    -F "file=@$($file.FullName)" `
    "$BaseUrl/file/upload"
  $upload = $uploadRaw | ConvertFrom-Json

  if ($upload.code -ne 200) {
    throw "Upload failed: $($file.Name) - $($upload.message)"
  }

  $docBody = New-DocumentJson $title $category $upload.data
  $bodyFile = [System.IO.Path]::GetTempFileName()
  try {
    [System.IO.File]::WriteAllText($bodyFile, $docBody, [System.Text.Encoding]::ASCII)
    $docRaw = & curl.exe -s `
      -X POST `
      -H "Authorization: Bearer $($login.data.token)" `
      -H "Content-Type: application/json" `
      --data-binary "@$bodyFile" `
      "$BaseUrl/qa/document"
    $doc = $docRaw | ConvertFrom-Json
  } finally {
    Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue
  }

  if ($doc.code -ne 200) {
    throw "Create document failed: $($file.Name) - $($doc.message)"
  }

  Write-Host "Done: $title -> documentId=$($doc.data.id)"
}

Write-Host "Imported $($files.Count) files."
