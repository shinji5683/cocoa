$xmlFiles = Get-ChildItem -Recurse app\src\main\res\*.xml
foreach ($f in $xmlFiles) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    if ($content -match "@color/cocoa_") {
        $newContent = $content -replace "@color/cocoa_", "@color/serena_"
        Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
    }
}
Write-Host "Updated all @color/cocoa_ references to @color/serena_ successfully!"
