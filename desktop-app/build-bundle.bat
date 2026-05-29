@echo off
REM ============================================================
REM  Полная сборка Malaria Detection одним приложением:
REM    ML API (PyInstaller .exe) + desktop (Compose) со встроенным API.
REM  Требования: JDK 17 (Temurin) и Python 3.12 (py -3.12).
REM ============================================================
setlocal

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "DESKTOP=%~dp0"
set "API=%~dp0..\ml\api"

echo.
echo === [1/4] Python venv + зависимости ML API ===
if not exist "%API%\.venv\Scripts\python.exe" (
    py -3.12 -m venv "%API%\.venv" || goto :fail
    "%API%\.venv\Scripts\python.exe" -m pip install --upgrade pip || goto :fail
    "%API%\.venv\Scripts\python.exe" -m pip install -r "%API%\requirements.txt" || goto :fail
    "%API%\.venv\Scripts\python.exe" -m pip install "pyinstaller>=6.0" || goto :fail
)

echo.
echo === [2/4] Сборка api-server.exe (PyInstaller) ===
pushd "%API%"
".venv\Scripts\pyinstaller.exe" api-server.spec --noconfirm --clean || (popd ^& goto :fail)
popd

echo.
echo === [3/4] Копирование ML API в ресурсы desktop ===
set "RES=%DESKTOP%resources\windows\api-server"
if exist "%RES%" rmdir /s /q "%RES%"
xcopy /e /i /y /q "%API%\dist\api-server" "%RES%" >nul || goto :fail

echo.
echo === [4/4] Сборка desktop-приложения (app image) ===
pushd "%DESKTOP%"
call gradlew.bat createDistributable || (popd ^& goto :fail)
popd

echo.
echo ============================================================
echo  Готово. Запуск приложения:
echo  "%DESKTOP%build\compose\binaries\main\app\Malaria Detection\Malaria Detection.exe"
echo.
echo  Установщик MSI (по желанию): gradlew.bat packageMsi
echo ============================================================
exit /b 0

:fail
echo.
echo !!! Сборка прервана с ошибкой (код %errorlevel%).
exit /b 1
