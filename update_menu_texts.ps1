$ktFiles = Get-ChildItem -Recurse app\src\main\java\com\shinji\serena\*.kt
foreach ($f in $ktFiles) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $newContent = $content -replace "cocoa", "serena" -replace "Cocoa", "Serena"
    Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
}

$xmlFiles = Get-ChildItem -Recurse app\src\main\res\*.xml
foreach ($f in $xmlFiles) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $newContent = $content -replace "cocoa", "serena" -replace "Cocoa", "Serena"
    Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
}

Write-Host "All menu and display texts updated to Serena successfully!"
