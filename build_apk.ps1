$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\shinj\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\shinj\AppData\Local\Android\Sdk"

Write-Host "Building serena Screen Reader Alpha Release and Debug APKs..."
.\gradlew.bat :app:assembleRelease :app:assembleDebug --stacktrace

Write-Host "Syncing APKs to Desktop/serena and Downloads/serena..."
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Desktop\serena" | Out-Null
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Downloads\serena" | Out-Null
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Desktop\serena\app-serena-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Desktop\serena\app-serena-debug.apk" -Force
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Downloads\serena\app-serena-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Downloads\serena\app-serena-debug.apk" -Force
Write-Host "serena Screen Reader Alpha APK export completed successfully."

Write-Host "Attempting auto-install and auto-activation on connected ADB devices..."
$deviceLines = adb devices | Select-String "\tdevice$"
if ($deviceLines) {
    foreach ($line in $deviceLines) {
        $devId = $line.ToString().Split("`t")[0].Trim()
        Write-Host "Deploying to device: $devId..."
        adb -s $devId install -r "app\build\outputs\apk\debug\app-debug.apk"
        adb -s $devId shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
        adb -s $devId shell settings put secure accessibility_enabled 1
        adb -s $devId shell am start -n com.shinji.serena/.MainActivity
    }
    Write-Host "serena Screen Reader deployed and auto-activated successfully! User-driven permission prompt ready."
} else {
    Write-Host "No active ADB device detected for auto-deploy. APKs saved to Desktop and Downloads."
}
