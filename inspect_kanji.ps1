$raw = Get-Content -Raw -Path 'kanji_raw.json' -Encoding UTF8 | ConvertFrom-Json
$props = $raw.PSObject.Properties | Select-Object -First 5
foreach ($p in $props) {
    Write-Host "Key: $($p.Name)"
    $p.Value | ConvertTo-Json -Depth 5
}
