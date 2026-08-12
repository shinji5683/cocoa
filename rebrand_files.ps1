$dir = "app\src\main\java\com\shinji\serena"

# 1. ファイルのリネーム
if (Test-Path "$dir\CocoaScreenReaderService.kt") {
    Rename-Item "$dir\CocoaScreenReaderService.kt" "SerenaScreenReaderService.kt" -Force
}
if (Test-Path "$dir\CocoaMenuDialog.kt") {
    Rename-Item "$dir\CocoaMenuDialog.kt" "SerenaMenuDialog.kt" -Force
}
if (Test-Path "$dir\CocoaAiAssistantHelper.kt") {
    Rename-Item "$dir\CocoaAiAssistantHelper.kt" "SerenaAiAssistantHelper.kt" -Force
}

if (Test-Path "app\src\main\res\layout\dialog_cocoa_menu.xml") {
    Rename-Item "app\src\main\res\layout\dialog_cocoa_menu.xml" "dialog_serena_menu.xml" -Force
}
if (Test-Path "app\src\main\res\layout\item_cocoa_menu.xml") {
    Rename-Item "app\src\main\res\layout\item_cocoa_menu.xml" "item_serena_menu.xml" -Force
}

# 2. 全Kotlinファイル内のクラス名・ファイル名参照置換
$ktFiles = Get-ChildItem -Recurse app\src\main\java\com\shinji\serena\*.kt
foreach ($f in $ktFiles) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $newContent = $content -replace "CocoaScreenReaderService", "SerenaScreenReaderService" `
                           -replace "CocoaMenuDialog", "SerenaMenuDialog" `
                           -replace "CocoaAiAssistantHelper", "SerenaAiAssistantHelper" `
                           -replace "CocoaMenuItem", "SerenaMenuItem" `
                           -replace "dialog_cocoa_menu", "dialog_serena_menu" `
                           -replace "item_cocoa_menu", "item_serena_menu" `
                           -replace "cocoa_prefs", "serena_prefs" `
                           -replace "cocoa_notification_filter_prefs", "serena_notification_filter_prefs" `
                           -replace "cocoa_clipboard_prefs", "serena_clipboard_prefs" `
                           -replace "☕ cocoa", "🌸 serena" `
                           -replace "✏️ cocoa", "✏️ serena"
    Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
}

# 3. AndroidManifest.xml および レイアウト XML の置換
$xmlFiles = Get-ChildItem -Recurse app\src\main\res\*.xml
foreach ($f in $xmlFiles) {
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $newContent = $content -replace "CocoaScreenReaderService", "SerenaScreenReaderService" `
                           -replace "CocoaMenuDialog", "SerenaMenuDialog" `
                           -replace "CocoaAiAssistantHelper", "SerenaAiAssistantHelper" `
                           -replace "dialog_cocoa_menu", "dialog_serena_menu" `
                           -replace "item_cocoa_menu", "item_serena_menu"
    Set-Content -Path $f.FullName -Value $newContent -Encoding UTF8
}

$manifestPath = "app\src\main\AndroidManifest.xml"
if (Test-Path $manifestPath) {
    $mContent = Get-Content $manifestPath -Raw -Encoding UTF8
    $mNew = $mContent -replace "CocoaScreenReaderService", "SerenaScreenReaderService"
    Set-Content -Path $manifestPath -Value $mNew -Encoding UTF8
}

Write-Host "Rebranded all Cocoa class and file names to Serena successfully!"
