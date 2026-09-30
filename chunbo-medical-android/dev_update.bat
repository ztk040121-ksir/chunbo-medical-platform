@echo off
setlocal
cd /d "%~dp0"
echo ========================================================
echo   Chunbo Medical Android - Fast Incremental Deploy
echo ========================================================
echo.

set "JAVA_HOME=E:\Study_Cache\jdk17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "ADB_PATH=E:\Study_Cache\AndroidSDK\platform-tools\adb.exe"

if not exist "%ADB_PATH%" (
    echo [ERROR] adb.exe not found in E:\Study_Cache\AndroidSDK\platform-tools
    pause
    exit /b 1
)

echo [1/4] Reverse port 8080 to phone...
"%ADB_PATH%" reverse tcp:8080 tcp:8080 >nul 2>&1

echo [2/4] Gradle incremental compiling APK...
call gradlew.bat --gradle-user-home "E:\Demo\Chunbo_Wanxiang_Medical\chunbo-medical-android\.gradle" assembleDebug
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

echo [3/4] Pushing APK to phone Download directory...
"%ADB_PATH%" push "app\build\outputs\apk\debug\app-debug.apk" /sdcard/Download/chunbo-medical.apk

echo [4/4] Triggering installation on phone screen...
"%ADB_PATH%" shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/chunbo-medical.apk -t application/vnd.android.package-archive >nul 2>&1

echo.
echo ========================================================
echo   APK 已成功推送并触发手机端安装界面！
echo   请在手机屏幕上点击【允许 / 继续安装】即可立即使用！
echo ========================================================
pause