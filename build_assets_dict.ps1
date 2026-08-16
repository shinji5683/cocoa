$dict = @{}

$dicPath = Join-Path (Get-Location) "characterDescriptions.dic"
$lines = [System.IO.File]::ReadAllLines($dicPath, [System.Text.Encoding]::UTF8)

foreach ($line in $lines) {
    if ($line.StartsWith("#") -or [string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    
    $parts = $line.Split("`t")
    if ($parts.Length -ge 2) {
        $char = $parts[0]
        $desc = $parts[1].Trim()
        if ($char.Length -eq 1 -and $desc.Length -gt 0) {
            $dict[$char] = $desc
        }
    }
}

Write-Host "Total entries parsed: $($dict.Count)"

$assetsDir = Join-Path (Get-Location) "app\src\main\assets"
if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
}

$json = $dict | ConvertTo-Json -Depth 2
$outPath = Join-Path $assetsDir "kanji_descriptions.json"
[System.IO.File]::WriteAllText($outPath, $json, [System.Text.Encoding]::UTF8)

Write-Host "Wrote $outPath successfully! File size: $((Get-Item $outPath).Length)"
