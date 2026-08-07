set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=C:\Users\shinj\AppData\Local\Android\Sdk
call gradlew.bat :app:assembleDebug --stacktrace > build_output.txt 2>&1
