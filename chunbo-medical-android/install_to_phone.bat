@echo off
cd /d "%~dp0"
echo ========================================================
echo   Chunbo Medical Android - Install to Real Device
echo ========================================================
echo.

set "ADB_PATH=E:\Study_Cache\AndroidSDK\platform-tools\adb.exe"
set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"

if not exist "%ADB_PATH%" (
    echo [ERROR] adb.exe not found in E:\Study_Cache\AndroidSDK\platform-tools
    pause
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo [ERROR] APK file not found at %APK_PATH%
    echo Please run build first!
    pause
    exit /b 1
)

echo [1/4] Checking connected devices...
"%ADB_PATH%" devices
echo.

echo [2/4] Setting up reverse proxy (phone 8080 to PC 8080)...
"%ADB_PATH%" reverse tcp:8080 tcp:8080
echo.

echo [3/4] Installing APK to Android phone...
"%ADB_PATH%" install -r "%APK_PATH%"
if errorlevel 1 (
    echo.
    echo [FAILED] Installation failed.
    echo Please check:
    echo 1. USB cable is connected.
    echo 2. Developer options - USB debugging is enabled on phone.
    echo 3. Allow USB installation on your phone prompt screen.
    echo.
    pause
    exit /b 1
)

echo.
echo [4/4] Launching Chunbo Medical App...
"%ADB_PATH%" shell am start -n com.chunbo.medical/.ui.MainActivity

echo.
echo ========================================================
echo   Successfully installed and launched!
echo ========================================================
pause