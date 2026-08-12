$files = Get-ChildItem -Recurse app\src\main\res\*.xml
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    if ($content -match "com\.shinji\.cocoa") {
        $newContent = $content -replace "com\.shinji\.cocoa", "com.shinji.serena"
        Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
    }
}
Write-Host "XML Package rename completed successfully!"
