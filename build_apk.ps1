$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\shinj\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\shinj\AppData\Local\Android\Sdk"

Write-Host "Building cocoa Alpha Release and Debug APKs..."
.\gradlew.bat :app:assembleRelease :app:assembleDebug --stacktrace

Write-Host "Syncing APKs to Desktop/cocoa and Downloads/cocoa..."
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Desktop\cocoa" | Out-Null
New-Item -ItemType Directory -Force -Path "C:\Users\shinj\Downloads\cocoa" | Out-Null
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Desktop\cocoa\app-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Desktop\cocoa\app-debug.apk" -Force
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\shinj\Downloads\cocoa\app-release.apk" -Force
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\shinj\Downloads\cocoa\app-debug.apk" -Force
Write-Host "Alpha APK export completed successfully."
