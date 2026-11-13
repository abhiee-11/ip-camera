@echo off
REM Gradle wrapper batch script

set GRADLE_VERSION=8.5
set GRADLE_DIST=gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_URL=https://services.gradle.org/distributions/%GRADLE_DIST%

set GRADLE_USER_HOME=%GRADLE_USER_HOME%
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle

set WRAPPER_DIR=%GRADLE_USER_HOME%\wrapper\dists\%GRADLE_DIST%

if not exist "%WRAPPER_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat" (
    echo Downloading Gradle %GRADLE_VERSION%...
    mkdir "%WRAPPER_DIR%" 2>nul
    cd /d "%WRAPPER_DIR%"
    powershell -Command "Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_DIST%'"
    powershell -Command "Expand-Archive -Path '%GRADLE_DIST%' -DestinationPath '.' -Force"
)

"%WRAPPER_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat" %*