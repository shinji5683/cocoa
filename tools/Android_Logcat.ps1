# Android Logcat を実行し、特定のタグのみ表示する設定です。
Write-Host "--- Android Logcat Tool ---" -ForegroundColor Cyan

# 1. 接続されているデバイス一覧を取得
$devices = adb devices | Select-String -Pattern "device$" | ForEach-Object {
    $parts = $_.Line.Split("`t")
    [PSCustomObject]@{
        Id = $parts[0]
        Type = if ($parts[0] -like "*_tcp" -or $parts[0] -like "*:*") { "WiFi" } else { "USB" }
    }
}

if ($devices.Count -eq 0) {
    Write-Host "No connected devices found. Please connect a device via USB or WiFi." -ForegroundColor Red
    Exit
}

$selectedDevice = $null

if ($devices.Count -eq 1) {
    # デバイスが1台だけなら自動選択
    $selectedDevice = $devices[0].Id
    Write-Host "Automatically selected only connected device: $selectedDevice ($($devices[0].Type))" -ForegroundColor Green
} else {
    # 複数デバイスがある場合はメニュー表示
    Write-Host "Multiple devices detected. Please select one:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "[$($i + 1)] ID: $($devices[$i].Id) ($($devices[$i].Type))"
    }

    $choice = ""
    while ($choice -lt 1 -or $choice -gt $devices.Count) {
        $choice = Read-Host "Select device (1-$($devices.Count))"
        if ($choice -match '^\d+$') {
            $choice = [int]$choice
        } else {
            $choice = 0
        }
    }
    $selectedDevice = $devices[$choice - 1].Id
}

Write-Host "--- Starting ADB Logcat for $selectedDevice (Press Ctrl+C to stop) ---" -ForegroundColor Cyan
adb -s $selectedDevice logcat -v time | Select-String "serena"

Write-Host "Log cat session stopped." -ForegroundColor Yellow
