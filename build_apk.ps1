$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\shinj\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\shinj\AppData\Local\Android\Sdk"

Write-Host "Building cocoa APK..."
.\gradlew.bat :app:assembleDebug --stacktrace
