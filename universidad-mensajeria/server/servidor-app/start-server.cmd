@echo off
rem Lanzador del servidor distribuido (DIST-02). Vive junto a servidor.jar.
rem Pasa los argumentos que se le den al JAR: los de Spring Boot prevalecen
rem sobre los empaquetados, p.ej.
rem   start-server.cmd --db.mysql.url=jdbc:mysql://127.0.0.1:3306/mensajeria_db --db.mysql.user=app --db.mysql.pass=CLAVE
rem Ver DESPLIEGUE.md antes de produccion (rotacion de secretos, firewall).
setlocal
cd /d "%~dp0"

if not exist "servidor.jar" (
    echo [ERROR] Falta servidor.jar. Copia la carpeta de distribucion completa.
    pause
    exit /b 1
)
if not exist "config\usuarios.csv" (
    echo [AVISO] Sin config\usuarios.csv: se usa la semilla de ejemplo del JAR.
    echo         En produccion, coloca aqui el CSV definitivo de usuarios.
)

java -jar servidor.jar ^
  --app.consola=false ^
  --app.escritorio=false ^
  --red.upload-dir="%~dp0uploads" ^
  --logging.file.name="%~dp0logs\server.log" ^
  %*

if errorlevel 1 pause
