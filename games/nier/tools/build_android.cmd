@echo off
rem Android build of NieR:Automata: lib nier.so (NDK) and private\nier\apk\nier.apk, through android\build_apk.ps1. cmake
rem and ninja come from the venv, the Vulkan headers from private\third_party. Usage: build_android.cmd [-Install]
setlocal
cd /d "%~dp0..\..\.."
set "PATH=%CD%\private\venv\Scripts;%PATH%"
set "VULKAN_SDK=%CD%\private\third_party\Vulkan-Headers-1.4.357"
powershell -NoProfile -ExecutionPolicy Bypass -File android\build_apk.ps1 -Game nier %*
