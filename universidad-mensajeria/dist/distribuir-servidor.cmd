@echo off
rem DIST-02: carpeta nativa del servidor (Windows).
rem Salida: dist\MensajeriaServidor\ (servidor.jar + start-server.cmd + config\ + uploads\ + logs\ + README.md)
rem Uso: cmd dist\distribuir-servidor.cmd   (se ejecuta desde la raiz universidad-mensajeria)
setlocal EnableDelayedExpansion

cd /d "%~dp0\.."
if not exist "pom.xml" (
    echo [ERROR] Falta pom.xml: ejecutar desde universidad-mensajeria.
    exit /b 1
)

set DIST=dist\MensajeriaServidor

echo [1/4] Compilando servidor...
call mvn -pl server/servidor-app -am install -DskipTests
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion del servidor.
    exit /b 1
)

echo [2/4] Preparando carpeta de distribucion %DIST% ...
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%" 2>nul
mkdir "%DIST%\config" 2>nul
mkdir "%DIST%\uploads" 2>nul
mkdir "%DIST%\logs" 2>nul

echo [3/4] Copiando JAR, lanzador y configuracion...
copy /y "server\servidor-app\target\servidor-app-1.0.0-SNAPSHOT.jar" "%DIST%\servidor.jar" >nul
if errorlevel 1 (
    echo [ERROR] No se hallo servidor-app-1.0.0-SNAPSHOT.jar.
    exit /b 1
)
copy /y "server\servidor-app\start-server.cmd" "%DIST%\start-server.cmd" >nul
copy /y "server\servidor-app\README.md" "%DIST%\README.md" >nul
if exist "server\servidor-app\src\main\resources\usuarios_iniciales.csv" (
    copy /y "server\servidor-app\src\main\resources\usuarios_iniciales.csv" "%DIST%\config\usuarios.csv" >nul
)
> "%DIST%\version.txt" echo MensajeriaServidor 1.0.0 %DATE% %TIME%

echo [4/4] Verificando distribucion...
if not exist "%DIST%\servidor.jar" (
    echo [ERROR] Falta %DIST%\servidor.jar.
    exit /b 1
)
if not exist "%DIST%\start-server.cmd" (
    echo [ERROR] Falta %DIST%\start-server.cmd.
    exit /b 1
)

echo [OK] Distribucion del servidor lista en %DIST%.
exit /b 0
