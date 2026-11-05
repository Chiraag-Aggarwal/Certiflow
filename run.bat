@echo off
setlocal EnableExtensions DisableDelayedExpansion
rem Double-click to compile the current sources offline, then open Certiflow.
rem Keep .tools, lib, src, and this launcher together when moving the project.
cd /d "%~dp0"
if errorlevel 1 goto :folder_error
set "PROJECT_DIR=%CD%"
set "JAVA_HOME=%PROJECT_DIR%\.tools\jdk-17"

if not exist "build.bat" goto :missing_build
if not exist "%JAVA_HOME%\bin\javaw.exe" goto :missing_java
if not exist "lib\javafx\javafx-base-*-win.jar" goto :missing_libraries
if not exist "lib\javafx\javafx-controls-*-win.jar" goto :missing_libraries
if not exist "lib\javafx\javafx-fxml-*-win.jar" goto :missing_libraries
if not exist "lib\javafx\javafx-graphics-*-win.jar" goto :missing_libraries
if not exist "lib\ikonli\ikonli-javafx-*.jar" goto :missing_libraries

call "%PROJECT_DIR%\build.bat" compile
if errorlevel 1 goto :build_failed
if not exist "target\classes\com\certiflow\App.class" goto :missing_classes

echo Opening Certiflow...
start "Certiflow" /D "%PROJECT_DIR%" "%JAVA_HOME%\bin\javaw.exe" -Dfile.encoding=UTF-8 -Dcertiflow.data.dir=data --module-path "lib\javafx" --add-modules javafx.controls,javafx.fxml --class-path "target\classes;lib\ikonli\*;lib\cloud\*" com.certiflow.App >>"logs\runtime.log" 2>&1
if errorlevel 1 goto :launch_failed
exit /b 0

:folder_error
echo Could not open the Certiflow project folder.
goto :failed

:missing_build
echo build.bat is missing. Restore the complete Certiflow project folder.
goto :failed

:missing_java
echo The bundled Java 17 runtime is missing from .tools\jdk-17.
echo Restore the complete project folder, including its .tools directory.
goto :failed

:missing_libraries
echo Required JavaFX or Ikonli libraries are missing from the lib directory.
echo Restore the complete project folder, including lib\javafx and lib\ikonli.
goto :failed

:build_failed
echo.
echo Certiflow could not be compiled. The app was not started.
echo Review the error above or open logs\build.log for the full build output.
goto :failed

:missing_classes
echo Compilation finished but the Certiflow application class is missing.
echo Review logs\build.log and restore any missing source files.
goto :failed

:launch_failed
echo Windows could not open Certiflow. Check logs\runtime.log for details.
goto :failed

:failed
echo.
pause
exit /b 1
