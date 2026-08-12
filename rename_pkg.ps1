$files = Get-ChildItem -Recurse app\src\main\java\com\shinji\serena\*.kt
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $newContent = $content -replace "com\.shinji\.cocoa", "com.shinji.serena"
    Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
}
Write-Host "Package rename completed successfully!"
