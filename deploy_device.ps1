$deviceLines = adb devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }
if ($deviceLines) {
    foreach ($line in $deviceLines) {
        $devId = $line.ToString().Split("`t")[0].Trim().Split(" ")[0].Trim()
        Write-Host "Deploying 100% 16KB-safe APK to device: $devId..."
        adb -s $devId install -r "app\build\outputs\apk\debug\app-debug.apk"
        
        Write-Host "Granting all essential runtime permissions on $devId..."
        adb -s $devId shell pm grant com.shinji.serena android.permission.RECORD_AUDIO
        adb -s $devId shell pm grant com.shinji.serena android.permission.CAMERA
        adb -s $devId shell pm grant com.shinji.serena android.permission.READ_PHONE_STATE
        adb -s $devId shell pm grant com.shinji.serena android.permission.ANSWER_PHONE_CALLS
        adb -s $devId shell pm grant com.shinji.serena android.permission.CALL_PHONE
        adb -s $devId shell pm grant com.shinji.serena android.permission.READ_CALL_LOG
        adb -s $devId shell pm grant com.shinji.serena android.permission.ACCESS_FINE_LOCATION
        adb -s $devId shell pm grant com.shinji.serena android.permission.ACCESS_COARSE_LOCATION
        adb -s $devId shell pm grant com.shinji.serena android.permission.POST_NOTIFICATIONS

        Write-Host "Auto-enabling Serena Accessibility Service on $devId..."
        adb -s $devId shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
        adb -s $devId shell settings put secure accessibility_enabled 1
        
        Write-Host "Enabling Serena IME on $devId..."
        adb -s $devId shell ime enable com.shinji.serena/.ime.SerenaInputMethodService
        
        Write-Host "Launching MainActivity for user-driven permission approval..."
        adb -s $devId shell am start -n com.shinji.serena/.MainActivity
    }
    Write-Host "DEPLOY_SUCCESS"
} else {
    Write-Host "NO_ADB_DEVICE_DETECTED"
}
