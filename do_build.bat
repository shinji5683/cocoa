@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=C:\Users\shinj\AppData\Local\Android\Sdk

echo Building Debug and Release APKs...
call gradlew.bat :app:assembleDebug :app:assembleRelease --stacktrace > build_output.txt 2>&1

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "C:\Users\shinj\Desktop\cocoa_debug_v1.0.apk"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "C:\Users\shinj\Downloads\cocoa_debug_v1.0.apk"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "cocoa_debug_v1.0.apk"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "C:\Users\shinj\Desktop\cocoa_debug_v1.0.apk.bin"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "C:\Users\shinj\Downloads\cocoa_debug_v1.0.apk.bin"
)

if exist "app\build\outputs\apk\release\app-release.apk" (
    copy /Y "app\build\outputs\apk\release\app-release.apk" "C:\Users\shinj\Desktop\cocoa_release_v1.0.apk"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "C:\Users\shinj\Downloads\cocoa_release_v1.0.apk"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "cocoa_release_v1.0.apk"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "C:\Users\shinj\Desktop\cocoa_release_v1.0.apk.bin"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "C:\Users\shinj\Downloads\cocoa_release_v1.0.apk.bin"
)

echo Build and deployment to Desktop and Downloads completed.
