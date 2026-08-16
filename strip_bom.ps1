$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$xmlFiles = Get-ChildItem -Recurse app\src\main\res\*.xml
foreach ($f in $xmlFiles) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $text = [System.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
        [System.IO.File]::WriteAllText($f.FullName, $text, $utf8NoBom)
        Write-Host "Stripped BOM from: $($f.Name)"
    }
}
Write-Host "BOM stripping process complete."
