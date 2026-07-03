$stdin = [Console]::In.ReadToEnd()

try {
    $payload = $stdin | ConvertFrom-Json -ErrorAction Stop
} catch {
    exit 0
}

$filePath = [string]$payload.file_path
$sessionId = [string]$payload.session_id

if ([string]::IsNullOrWhiteSpace($filePath) -or [string]::IsNullOrWhiteSpace($sessionId)) {
    exit 0
}

if ($filePath -notmatch '\.(kt|kts|java|xml|gradle|properties|toml)$') {
    exit 0
}

$trackFile = Join-Path $env:TEMP "album_codex_edits_$sessionId.txt"
Add-Content -LiteralPath $trackFile -Value $filePath -Encoding UTF8

