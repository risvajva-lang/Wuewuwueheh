@echo off
setlocal
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Run this project on GitHub Actions or install Gradle 8.10.2 locally.
exit /b 1
