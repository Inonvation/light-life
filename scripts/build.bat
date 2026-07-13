@echo off
setlocal enabledelayedexpansion

set FILE=%USERPROFILE%\.devicecontrol-version
set SKIP=0
set VERSION=

:parse_args
if "%~1"=="--skip-version" set SKIP=1 & shift & goto parse_args
if "%~1"=="" goto :run
set VERSION=%~1

:run
if not defined VERSION (
    if exist %FILE% (
        set /p LAST_VERSION=<%FILE%
        for /f "tokens=1,2,3 delims=." %%a in ("!LAST_VERSION!") do (
            set /a PATCH=%%c+1
            set VERSION=%%a.%%b.!PATCH!
        )
    ) else (
        set VERSION=0.0.1
    )
)
if "%SKIP%"=="0" (
    echo [Build] Version: %VERSION%
    >%FILE% echo %VERSION%
) else (
    if exist %FILE% set /p VERSION=<%FILE%
    echo [Build] Version: %VERSION% (no bump)
)
echo [Build] Compiling...
call .\gradlew.bat :app:archiveDebugApk -PbuildVersionCode=1 -PbuildVersionName="%VERSION%"
if errorlevel 1 (
    echo [FAILED] Build error
    pause
    exit /b 1
)
echo [OK] APK: archive\app-debug-v%VERSION%.apk
echo [OK] Version: %VERSION%
pause
