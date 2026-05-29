@echo off
cd /d "%~dp0"
start "Malaria ML API" "%~dp0run-api.bat"
timeout /t 8 /nobreak > nul
call "%~dp0run-desktop.bat"
