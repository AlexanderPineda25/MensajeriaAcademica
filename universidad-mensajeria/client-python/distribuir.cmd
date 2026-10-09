@echo off
rem DIST-02: carpeta nativa del cliente Python (Windows).
rem Salida: dist/MensajeriaPython/ (fuentes sin .venv, con requirements + esquema local).
rem En destino: crear venv e instalar requirements (ver DESPLIEGUE.md).
setlocal

cd /d "%~dp0"
if not exist "main.py" (
    echo [ERROR] Falta main.py: ejecutar desde client-python.
    exit /b 1
)

set DIST=dist\MensajeriaPython
if exist "dist" rmdir /s /q "dist"

echo [1/3] Copiando fuentes y recursos visuales (sin .venv, caches, tests ni bases)...
mkdir "%DIST%" 2>nul
robocopy . "%DIST%" /E /NFL /NDL /NJH /NJS ^
    /XD .venv __pycache__ tests .pytest_cache imagenes dist ^
    /XF *.db *.db-journal *.db-wal *.pyc
if %ERRORLEVEL% GEQ 8 (
    echo [ERROR] Fallo robocopy ^(nivel %ERRORLEVEL%^).
    exit /b 1
)

echo [2/3] Verificando contenido minimo...
for %%F in (main.py requirements.txt client.properties store\schema-local.sql fachada\fachada_cliente.py ui\chat_window.py ui\fondos\fondo_chat_claro.jpg ui\fondos\fondo_chat_oscuro.jpg) do (
    if not exist "%DIST%\%%F" (
        echo [ERROR] Falta %DIST%\%%F.
        exit /b 1
    )
)

echo [3/3] Compilacion sintactica con el venv local...
if exist ".venv\Scripts\python.exe" (
    .venv\Scripts\python.exe -m compileall -q "%DIST%"
    if errorlevel 1 (
        echo [ERROR] Fallo compileall en la distribucion.
        exit /b 1
    )
    rem compileall regenera __pycache__: se retiran del distribuible.
    for /d /r "%DIST%" %%D in (__pycache__) do @if exist "%%D" rmdir /s /q "%%D"
) else (
    echo [AVISO] Sin .venv local: se omite compileall.
)
> "%DIST%\version.txt" echo MensajeriaPython 1.0.0 %DATE% %TIME%

echo [OK] Distribucion lista en %DIST%.
dir "%DIST%" | findstr /v "^ "
rem findstr devuelve 1 cuando no filtra lineas: el codigo de salida real
rem del script es el del paso 3 (0 = todo OK), no el del listado.
exit /b 0
