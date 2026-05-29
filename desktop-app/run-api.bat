@echo off
cd /d "%~dp0..\ml\api"
if not exist ".venv\Scripts\python.exe" (
    python -m venv .venv
    .venv\Scripts\python.exe -m pip install --upgrade pip
    .venv\Scripts\python.exe -m pip install -r requirements.txt
)
if not exist "best.pt" (
    copy "..\training\best.pt" "best.pt"
)
.venv\Scripts\python.exe app.py
