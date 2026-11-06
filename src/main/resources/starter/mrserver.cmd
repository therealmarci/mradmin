@echo off
rem ==============================================================================
rem   MRadmin starter (Windows) - starts the Paper server, brings it back after
rem   restarts and crashes and keeps Paper up to date. Settings: mrserver.properties
rem   and plugins\MRadmin\config.yml (section server). Guide:
rem   https://mrtechnology.marcelr.at/wiki/mradmin/starter/
rem
rem   New server:   put this file into an empty folder, then:  mrserver setup
rem   Help:         mrserver help
rem
rem   This file is replaced by MRadmin - do not edit it, use mrserver.properties.
rem ==============================================================================
setlocal
cd /d "%~dp0"
set "DIR=plugins\MRadmin\starter"
set "JAR=%DIR%\starter.jar"
set "JAVA=java"
if exist mrserver.properties (
    for /f "usebackq tokens=1,* delims==" %%a in ("mrserver.properties") do if "%%a"=="java" set "JAVA=%%b"
)
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Java not found ^(%JAVA%^). Paper needs Java 25 or newer: https://adoptium.net/
    exit /b 1
)

if exist "%JAR%" goto ready
rem First setup: no starter yet - use MRadmin from plugins\ or download the newest stable release.
set "PLUGIN="
set "DOWNLOADED="
for %%f in (plugins\MRadmin*.jar) do set "PLUGIN=%%f"
if not defined PLUGIN (
    echo Downloading MRadmin ...
    if not exist plugins mkdir plugins
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=Invoke-RestMethod 'https://api.github.com/repos/therealmarci/mradmin/releases/latest'; $a=$r.assets | Where-Object { $_.name -like 'MRadmin-mc*.jar' } | Select-Object -First 1; if (-not $a) { exit 1 }; Invoke-WebRequest $a.browser_download_url -OutFile ('plugins\' + $a.name)"
    if errorlevel 1 (
        echo Could not download MRadmin. Put MRadmin into plugins\ and run this again.
        exit /b 1
    )
    for %%f in (plugins\MRadmin*.jar) do set "PLUGIN=%%f"
    set "DOWNLOADED=--downloaded"
)
if /i "%~1"=="setup" shift
"%JAVA%" -jar "%PLUGIN%" setup %DOWNLOADED% %1 %2 %3 %4
exit /b %errorlevel%

:ready
if /i not "%~1"=="run" goto other
rem Loop only for a new starter version (exit code 3) - restarts of the server happen inside the starter.
set "MRSERVER_WRAPPER=1"
title MRadmin starter - %CD%
:loop
if exist "%DIR%\starter-new.jar" move /y "%DIR%\starter-new.jar" "%JAR%" >nul
"%JAVA%" -jar "%JAR%" run
if %errorlevel%==3 goto loop
exit /b %errorlevel%

:other
"%JAVA%" -jar "%JAR%" %*
exit /b %errorlevel%
