@echo off
REM serve-apk.bat - Serves built APKs over Wi-Fi for installation on the camera phone
REM Run from the FreeCam project root directory

set PROJECT_DIR=%~dp0..
set DIST_DIR=%PROJECT_DIR%\dist

echo Building APKs...
cd /d %PROJECT_DIR%
call gradlew.bat assembleDebug

echo Creating dist directory...
if not exist %DIST_DIR% mkdir %DIST_DIR%

echo Copying APKs...
for %%f in (%PROJECT_DIR%\camera\build\outputs\apk\debug\*.apk) do copy "%%f" "%DIST_DIR%\freecam-camera-debug.apk"
for %%f in (%PROJECT_DIR%\viewer\build\outputs\apk\debug\*.apk) do copy "%%f" "%DIST_DIR%\freecam-viewer-debug.apk"

if not exist "%DIST_DIR%\freecam-camera-debug.apk" (
    echo Error: Camera APK not found!
    exit /b 1
)
if not exist "%DIST_DIR%\freecam-viewer-debug.apk" (
    echo Error: Viewer APK not found!
    exit /b 1
)

REM Get local IP
for /f "tokens=2 delims=[]" %%a in ('ping -n 1 -4 "%computername%" ^| find "["') do set LOCAL_IP=%%a
if "%LOCAL_IP%"=="" set LOCAL_IP=localhost

echo.
echo ==========================================
echo APKs ready at:
echo   Camera:  http://%LOCAL_IP%:8000/freecam-camera-debug.apk
echo   Viewer:  http://%LOCAL_IP%:8000/freecam-viewer-debug.apk
echo.
echo Starting HTTP server on port 8000...
echo Open these URLs in the phone's browser to install.
echo ==========================================
echo.

cd /d %DIST_DIR%
python -m http.server 8000