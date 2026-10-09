@echo off
rem Script maestro para empaquetar los 4 componentes (Servidor + 3 Clientes)
rem Genera las 4 carpetas de distribucion listas para llevar a 4 computadores.
setlocal EnableDelayedExpansion

cd /d "%~dp0"
if not exist "pom.xml" (
    echo [ERROR] Ejecutar este script desde la carpeta raiz: universidad-mensajeria
    exit /b 1
)

echo ==============================================================================
echo   GENERADOR DE DISTRIBUCIONES PARA 4 COMPUTADORES (DIST-02)
echo ==============================================================================
echo.

rem 1. Servidor
echo [PASO 1/4] Empaquetando Servidor Central (Spring Boot + MySQL)...
call dist\distribuir-servidor.cmd
if errorlevel 1 (
    echo [ERROR] Fallo al empaquetar el Servidor.
    exit /b 1
)
echo.

rem 2. Cliente JavaFX
echo [PASO 2/4] Empaquetando Cliente JavaFX (Java 21)...
call client\dist-client.cmd
if errorlevel 1 (
    echo [ERROR] Fallo al empaquetar el Cliente JavaFX.
    exit /b 1
)
echo.

rem 3. Cliente Python
echo [PASO 3/4] Empaquetando Cliente Python (PySide6)...
call client-python\distribuir.cmd
if errorlevel 1 (
    echo [ERROR] Fallo al empaquetar el Cliente Python.
    exit /b 1
)
rem Copiar/reflejar en dist\MensajeriaPython para que todo quede centralizado en dist\
if exist "client-python\dist\MensajeriaPython" (
    if exist "dist\MensajeriaPython" rmdir /s /q "dist\MensajeriaPython"
    mkdir "dist\MensajeriaPython" 2>nul
    robocopy "client-python\dist\MensajeriaPython" "dist\MensajeriaPython" /E /NFL /NDL /NJH /NJS >nul
)
echo.

rem 4. Cliente .NET WPF
echo [PASO 4/4] Empaquetando Cliente .NET WPF (C# / Windows)...
where dotnet >nul 2>&1
if errorlevel 1 (
    echo [AVISO] dotnet SDK no encontrado en el PATH. Omitiendo empaquetado de WPF.
) else (
    call dotnet publish client-dotnet\Mensajeria.csproj -c Release -r win-x64 --self-contained true -o dist\MensajeriaWPF
    if errorlevel 1 (
        echo [ERROR] Fallo dotnet publish para el Cliente WPF.
    ) else (
        echo [OK] Cliente WPF listo en dist\MensajeriaWPF.
    )
)
echo.

echo ==============================================================================
echo   PROCESO COMPLETADO EXITOSAMENTE
echo ==============================================================================
echo Las 4 carpetas para distribuir en cada computador se encuentran en:
echo.
echo   PC 1 (Servidor):     dist\MensajeriaServidor\
echo   PC 2 (JavaFX):       dist\MensajeriaClienteJava\
echo   PC 3 (Python):       dist\MensajeriaPython\
echo   PC 4 (WPF .NET):     dist\MensajeriaWPF\
echo.
echo Consulta DESPLIEGUE.md para la guia de configuracion de red y arranque.
echo ==============================================================================
exit /b 0
