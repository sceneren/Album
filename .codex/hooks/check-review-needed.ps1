$stdin = [Console]::In.ReadToEnd()

try {
    $payload = $stdin | ConvertFrom-Json -ErrorAction Stop
} catch {
    exit 0
}

$sessionId = [string]$payload.session_id
if ([string]::IsNullOrWhiteSpace($sessionId)) {
    exit 0
}

$trackFile = Join-Path $env:TEMP "album_codex_edits_$sessionId.txt"
if (-not (Test-Path -LiteralPath $trackFile)) {
    exit 0
}

$fileCount = (Get-Content -LiteralPath $trackFile | Sort-Object -Unique | Measure-Object).Count
Remove-Item -LiteralPath $trackFile -Force

if ($fileCount -ge 2) {
    Write-Error "[project rule] $fileCount source/config files changed. Run the local code_review skill before finishing."
    exit 2
}

