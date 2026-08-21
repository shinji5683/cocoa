$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\shinj\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\shinj\AppData\Local\Android\Sdk"
$buildToolsPath = "C:\Users\shinj\AppData\Local\Android\Sdk\build-tools\36.0.0"
if (-not (Test-Path "$buildToolsPath\zipalign.exe")) {
    $buildToolsPath = "C:\Users\shinj\AppData\Local\Android\Sdk\build-tools\34.0.0"
}

Write-Host "Building Serena Screen Reader Alpha Release, Debug APKs, and Play Store Bundle (.aab)..."
.\gradlew.bat :app:assembleRelease :app:assembleDebug :app:bundleRelease --stacktrace

Write-Host "Executing 16KB Page Alignment (0x4000) ELF Patching on all APKs..."
function Patch-And-Align-Apk {
    param (
        [string]$apkPath,
        [string]$keyStorePath = "keystore\serena-release-key.jks",
        [string]$keyAlias = "serena",
        [string]$keyPass = "shinji5683",
        [bool]$isRelease = $false
    )

    if (-not (Test-Path $apkPath)) { return }
    Write-Host "Processing 16KB Alignment for: $apkPath"

    $tempPatched = "$apkPath.patched.apk"
    $tempAligned = "$apkPath.aligned.apk"

    # 1. Python ELF Header 16KB Patching (p_align = 16384)
    python patch_16k_elf.py $apkPath $tempPatched

    if (Test-Path $tempPatched) {
        # 2. zipalign -p 16384 (16KB page boundary alignment for uncompressed .so)
        & "$buildToolsPath\zipalign.exe" -p -f 16384 $tempPatched $tempAligned

        # 3. apksigner v2/v3/v4 resign
        if ($isRelease -and (Test-Path $keyStorePath)) {
            & "$buildToolsPath\apksigner.bat" sign --ks $keyStorePath --ks-key-alias $keyAlias --ks-pass "pass:$keyPass" --key-pass "pass:$keyPass" --out $apkPath $tempAligned
        } else {
            $debugKeystore = "$env:USERPROFILE\.android\debug.keystore"
            if (Test-Path $debugKeystore) {
                & "$buildToolsPath\apksigner.bat" sign --ks $debugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apkPath $tempAligned
            } else {
                Move-Item -Path $tempAligned -Destination $apkPath -Force
            }
        }

        Remove-Item $tempPatched -ErrorAction SilentlyContinue
        Remove-Item $tempAligned -ErrorAction SilentlyContinue
        Write-Host "  16KB Page Alignment & Signature applied successfully to $apkPath"
    }
}

Patch-And-Align-Apk -apkPath "app\build\outputs\apk\debug\app-debug.apk" -isRelease $false
Patch-And-Align-Apk -apkPath "app\build\outputs\apk\release\app-release.apk" -isRelease $true

Write-Host "Syncing 16KB Aligned APKs and Play Store Bundle (.aab) to Desktop/serena and Downloads/serena..."
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Desktop\serena" | Out-Null
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Downloads\serena" | Out-Null

Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Desktop\serena\app-serena-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Desktop\serena\app-serena-debug.apk" -Force
Copy-Item -Path "app\build\outputs\bundle\release\app-release.aab" -Destination "C:\Users\shinj\Desktop\serena\app-serena-release.aab" -Force

Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Downloads\serena\app-serena-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Downloads\serena\app-serena-debug.apk" -Force
Copy-Item -Path "app\build\outputs\bundle\release\app-release.aab" -Destination "C:\Users\shinj\Downloads\serena\app-serena-release.aab" -Force

Write-Host "Serena Screen Reader 16KB Aligned APK and Play Store AAB export completed successfully."

Write-Host "Attempting auto-install and auto-activation on connected ADB devices..."
$deviceLines = adb devices | Select-String "\tdevice$"
if ($deviceLines) {
    foreach ($line in $deviceLines) {
        $devId = $line.ToString().Split("`t")[0].Trim()
        Write-Host "Deploying 16KB Aligned APK to device: $devId..."
        adb -s $devId install -r "app\build\outputs\apk\debug\app-debug.apk"
        adb -s $devId shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
        adb -s $devId shell settings put secure accessibility_enabled 1
        adb -s $devId shell am start -n com.shinji.serena/.MainActivity
    }
    Write-Host "Serena Screen Reader 16KB Aligned APK deployed and auto-activated successfully!"
} else {
    Write-Host "No active ADB device detected for auto-deploy. APKs and AAB saved to Desktop and Downloads."
}
