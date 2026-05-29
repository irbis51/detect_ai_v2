# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller-спецификация сборки ML API в standalone-приложение.

Сборка (из каталога ml/api с активным venv на Python 3.12):
    .venv\\Scripts\\pyinstaller.exe api-server.spec --noconfirm

Результат: dist/api-server/api-server.exe (onedir) с моделью best.pt внутри.
Запускает FastAPI/uvicorn на http://127.0.0.1:8000.
"""
from PyInstaller.utils.hooks import collect_submodules

hiddenimports = []
hiddenimports += collect_submodules("uvicorn")
hiddenimports += collect_submodules("torchvision")
hiddenimports += ["multipart"]  # python-multipart, нужен starlette для form-data

datas = [("best.pt", ".")]

excludes = [
    "tkinter", "matplotlib", "PyQt5", "PySide2", "PySide6", "PyQt6",
    "IPython", "notebook", "pytest", "scipy",
]

a = Analysis(
    ["app.py"],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=excludes,
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="api-server",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="api-server",
)
