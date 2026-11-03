@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Usage: build.bat [test or compile]. Default: compile and run all tests.
rem This script uses only the bundled Java, Maven, and offline dependency cache.
cd /d "%~dp0"
if errorlevel 1 goto :folder_error
set "PROJECT_DIR=%CD%"
set "BUILD_GOAL=test"
if not "%~2"=="" goto :usage
if "%~1"=="" goto :goal_ready
if /I "%~1"=="test" goto :goal_ready
if /I "%~1"=="compile" goto :compile_only
goto :usage

:compile_only
set "BUILD_GOAL=compile"

:goal_ready
set "JAVA_HOME=%PROJECT_DIR%\.tools\jdk-17"
set "MAVEN_CMD=%PROJECT_DIR%\.tools\apache-maven-3.9.9\bin\mvn.cmd"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "%JAVA_HOME%\bin\javac.exe" goto :missing_java
if not exist "%MAVEN_CMD%" goto :missing_maven
if not exist ".tools\repository\" goto :missing_cache
if not exist "pom.xml" goto :missing_project
if not exist "logs\" mkdir "logs" 2>nul
if not exist "logs\" goto :write_error
if not exist "target\test-temp\" mkdir "target\test-temp" 2>nul
if not exist "target\test-temp\" goto :write_error

echo Building Certiflow (%BUILD_GOAL%) with bundled Java 17...
call "%MAVEN_CMD%" -o -B -ntp "-Dmaven.repo.local=%PROJECT_DIR%\.tools\repository" "-Djava.io.tmpdir=%PROJECT_DIR%\target\test-temp" %BUILD_GOAL% >"logs\build.log" 2>&1
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" goto :maven_failed
echo Build succeeded. Full output: logs\build.log
if /I "%BUILD_GOAL%"=="test" echo Test results: target\surefire-reports
exit /b 0

:maven_failed
echo Build failed. Maven output follows:
if exist "logs\build.log" type "logs\build.log"
echo.
echo The full build output is saved in logs\build.log.
echo If an artifact is missing, restore the complete .tools\repository cache.
exit /b %BUILD_EXIT%

:usage
echo Usage: build.bat [test ^| compile]
echo Default: test. Use compile to build without running the tests.
exit /b 2

:folder_error
echo Could not open the Certiflow project folder.
exit /b 1

:missing_java
echo The bundled Java 17 development kit is missing from .tools\jdk-17.
exit /b 1

:missing_maven
echo The bundled Maven installation is missing from .tools\apache-maven-3.9.9.
exit /b 1

:missing_cache
echo The offline dependency cache is missing from .tools\repository.
exit /b 1

:missing_project
echo pom.xml is missing. Run this script from the complete project folder.
exit /b 1

:write_error
echo Could not create the logs or build folders. Check this folder's write access.
exit /b 1
