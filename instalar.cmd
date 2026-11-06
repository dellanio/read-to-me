@echo off
setlocal
set ADB=C:\Ambiente\Android\Sdk\platform-tools\adb.exe

if "%~1"=="" (
    echo Informe o IP:PORTA do aparelho.
    echo Exemplo: instalar.cmd 192.168.0.15:41235
    goto :end
)

echo [1/3] Conectando ao aparelho em %1...
%ADB% connect %1

echo [2/3] Instalando ReadToMe APK...
%ADB% install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"

echo [3/3] Iniciando o aplicativo no celular...
%ADB% shell am start -n com.readtome.app/.ui.MainActivity

:end
endlocal
