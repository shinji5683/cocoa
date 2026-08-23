# Android Logcat を実行し、デバイス選択・モード選択・自動保存を行うツールですにょろ。
Write-Host "--- Android Logcat Tool ---" -ForegroundColor Cyan

# 1. 接続されているデバイス一覧を取得
$devices = adb devices | Select-String -Pattern "device$" | ForEach-Object {
    $parts = $_.Line -split "\t"
    $devType = "USB"
    if ($parts[0] -like "*_tcp" -or $parts[0] -like "*:*") {
        $devType = "WiFi"
    }
    New-Object PSObject -Property @{
        Id = $parts[0]
        Type = $devType
    }
}

if ($devices.Count -eq 0) {
    Write-Host "No connected devices found. Please connect a device via USB or WiFi." -ForegroundColor Red
    Exit
}

$selectedDevice = $null
if ($devices.Count -eq 1) {
    $selectedDevice = $devices[0].Id
    Write-Host "Automatically selected only connected device: $selectedDevice ($($devices[0].Type))" -ForegroundColor Green
} else {
    Write-Host "Multiple devices detected. Please select one:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "[$($i + 1)] ID: $($devices[$i].Id) ($($devices[$i].Type))"
    }
    $choice = ""
    while ($choice -lt 1 -or $choice -gt $devices.Count) {
        $choice = Read-Host "Select device (1-$($devices.Count))"
        if ($choice -match '^\d+$') { $choice = [int]$choice } else { $choice = 0 }
    }
    $selectedDevice = $devices[$choice - 1].Id
}

# 2. ログ取得モードの選択
Write-Host "`n--- Select Log Mode ---" -ForegroundColor Cyan
Write-Host "[1] Default Mode (Service & Gesture logs only)"
Write-Host "[2] Error Mode (Error & Fatal logs only)"
Write-Host "[3] Full Mode (All logs from screen reader)"

$modeChoice = ""
while ($modeChoice -ne "1" -and $modeChoice -ne "2" -and $modeChoice -ne "3") {
    $modeChoice = Read-Host "Select mode (1-3)"
}

$modeName = ""
$filterPattern = ""
$logFilterArgs = @()

if ($modeChoice -eq "1") {
    $modeName = "DefaultMode"
    $logFilterArgs = @("SerenaScreenReader:D", "SerenaGestureDispatcher:D", "*:S")
} elseif ($modeChoice -eq "2") {
    $modeName = "ErrorMode"
    $logFilterArgs = @("*:E")
    $filterPattern = "serena"
} else {
    $modeName = "FullMode"
    $logFilterArgs = @()
    $filterPattern = "serena"
}

# 3. ログのリアルタイム表示とバッファリング実行
$logLines = [System.Collections.Generic.List[string]]::new()
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "${modeName}_${timestamp}.log"

$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = "adb"
$processInfo.Arguments = "-s $selectedDevice logcat -v time $logFilterArgs"
$processInfo.RedirectStandardOutput = $true
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $processInfo

Write-Host "`n--- Starting ADB Logcat in [$modeName] ---" -ForegroundColor Cyan
Write-Host "Press [Ctrl+C] to stop and automatically save logs to a file." -ForegroundColor Yellow
Write-Host "--------------------------------------------------------"

[console]::TreatControlCAsInput = $true

try {
    $process.Start() | Out-Null
    while (-not $process.HasExited) {
        $ctrlCPressed = $false
        try {
            if ([console]::KeyAvailable) {
                $key = [console]::ReadKey($true)
                if ($key.Modifiers -eq [System.ConsoleModifiers]::Control -and $key.Key -eq [System.ConsoleKey]::C) {
                    $ctrlCPressed = $true
                }
            }
        } catch {
            # リダイレクト環境対策
        }
        
        if ($ctrlCPressed) {
            break
        }
        
        $line = $process.StandardOutput.ReadLine()
        if ($line -ne $null) {
            if ([string]::IsNullOrEmpty($filterPattern) -or $line -imatch $filterPattern) {
                Write-Host $line
                $logLines.Add($line)
            }
        } else {
            Start-Sleep -Milliseconds 10
        }
    }
} finally {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
    }
    
    if ($logLines.Count -gt 0) {
        Write-Host "`nSaving logs to disk... Please wait." -ForegroundColor Yellow
        $logLines | Out-File -FilePath $fileName -Encoding utf8
        while (-not (Test-Path $fileName)) {
            Start-Sleep -Milliseconds 50
        }
        Write-Host "Log cat session stopped." -ForegroundColor Yellow
        Write-Host "Logs successfully saved: [ $fileName ] (Total lines: $($logLines.Count))" -ForegroundColor Green
    } else {
        Write-Host "`nLog cat session stopped. No logs were captured." -ForegroundColor Yellow
    }
}
